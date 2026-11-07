package com.yesmenn.creates.compat.electroenergetics.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yesmenn.creates.compat.electroenergetics.EEDataComponents;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.GridPos;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.SurfaceWire;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.SurfaceWireItem;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.SurfaceWirePackets;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.SurfaceWirePlacement;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.WireStart;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Client side of the flat wires: mirror of the server's wires, rendering, placement preview and picking.
 * <p>
 * Wires are grouped by the chunk section they start in. Each section is meshed once into a static GPU
 * buffer (like a vanilla chunk section) and drawn with the solid block shader; it is only re-meshed when
 * its wires change, and every few seconds to pick up light changes. No per-wire work happens per frame.
 */
public final class SurfaceWireClient {
    private static final long LIGHT_REFRESH_MS = 2000;
    private static final int MAX_REBUILDS_PER_FRAME = 4;
    private static final float[] SHADE = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f}; // by Direction ordinal

    private static final Int2ObjectMap<SurfaceWire> WIRES = new Int2ObjectOpenHashMap<>();
    private static final Long2ObjectMap<Section> SECTIONS = new Long2ObjectOpenHashMap<>();

    /**
     * Which wire draws the cube of a wire end that several wires share (lowest id). Kept apart for hidden
     * (invisible) wires, which are drawn into their own buffer.
     */
    private static final Map<GridPos, Integer> ENDPOINT_OWNER = new HashMap<>();
    private static final Map<GridPos, Integer> HIDDEN_ENDPOINT_OWNER = new HashMap<>();

    private static String lastPreviewText = "";
    private static boolean airModeSent = false;

    private SurfaceWireClient() {}

    private static final class Section {
        final BlockPos origin;
        final IntSet wires = new IntOpenHashSet();
        VertexBuffer buffer;
        /** Invisible wires, only drawn while one is held. */
        VertexBuffer hiddenBuffer;
        boolean dirty = true;
        long builtAt;
        AABB bounds;

        Section(long key) {
            this.origin = SectionPos.of(key).origin();
        }

        void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
            if (hiddenBuffer != null) {
                hiddenBuffer.close();
                hiddenBuffer = null;
            }
        }
    }

    public static Collection<SurfaceWire> wires() {
        return WIRES.values();
    }

    // ------------------------------------------------------------------------------------------------
    // Sync (called from the packet handlers on the main thread)
    // ------------------------------------------------------------------------------------------------

    public static void handleSync(List<SurfaceWire> wires) {
        clear();
        wires.forEach(SurfaceWireClient::put);
    }

    public static void handleAdd(SurfaceWire wire) {
        put(wire);
    }

    public static void handleRemove(int id) {
        SurfaceWire wire = WIRES.remove(id);
        if (wire != null) {
            endpointsChanged(wire);
            Section section = SECTIONS.get(sectionKey(wire));
            if (section != null) {
                section.wires.remove(id);
                section.dirty = true;
                section.bounds = null;
                if (section.wires.isEmpty()) {
                    section.close();
                    SECTIONS.remove(sectionKey(wire));
                }
            }
        }
    }

    private static void put(SurfaceWire wire) {
        handleRemove(wire.id());
        WIRES.put(wire.id(), wire);
        endpointsChanged(wire);
        Section section = SECTIONS.computeIfAbsent(sectionKey(wire), Section::new);
        section.wires.add(wire.id());
        section.dirty = true;
        section.bounds = null;
    }

    /** Recomputes the end cube owners and re-meshes the wires sharing an end with {@code changed}. */
    private static void endpointsChanged(SurfaceWire changed) {
        ENDPOINT_OWNER.clear();
        HIDDEN_ENDPOINT_OWNER.clear();
        for (SurfaceWire wire : WIRES.values()) {
            Map<GridPos, Integer> owners = owners(wire);
            owners.merge(wire.start(), wire.id(), Math::min);
            owners.merge(wire.end(), wire.id(), Math::min);
        }
        for (SurfaceWire wire : WIRES.values()) {
            if (wire.start().equals(changed.start()) || wire.start().equals(changed.end())
                    || wire.end().equals(changed.start()) || wire.end().equals(changed.end())) {
                Section section = SECTIONS.get(sectionKey(wire));
                if (section != null) {
                    section.dirty = true;
                }
            }
        }
    }

    /** Ctrl held: lay the wire through the air. */
    public static boolean isAirMode() {
        return Screen.hasControlDown();
    }

    /** Tells the server whether Ctrl is held while a flat wire is in hand. */
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean airMode = player != null && !heldWire(player).isEmpty() && isAirMode();
        if (player != null && airMode != airModeSent) {
            airModeSent = airMode;
            PacketDistributor.sendToServer(new SurfaceWirePackets.AirMode(airMode));
        }
    }

    private static Map<GridPos, Integer> owners(SurfaceWire wire) {
        return wire.material().hidden() ? HIDDEN_ENDPOINT_OWNER : ENDPOINT_OWNER;
    }

    /** Invisible wires are shown while one is held. */
    private static boolean showHidden(Player player) {
        return heldWire(player).getItem() instanceof SurfaceWireItem item && item.getMaterial().hidden();
    }

    private static long sectionKey(SurfaceWire wire) {
        return SectionPos.asLong(wire.start().blockPos());
    }

    private static void clear() {
        SECTIONS.values().forEach(Section::close);
        SECTIONS.clear();
        WIRES.clear();
        ENDPOINT_OWNER.clear();
        HIDDEN_ENDPOINT_OWNER.clear();
        airModeSent = false;
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void requestRemove(int id) {
        PacketDistributor.sendToServer(new SurfaceWirePackets.RemoveRequest(id));
    }

    // ------------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------------

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            renderWires(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            renderPreview(event);
        }
    }

    private static void renderWires(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || SECTIONS.isEmpty()) {
            return;
        }
        Frustum frustum = event.getFrustum();
        boolean showHidden = mc.player != null && showHidden(mc.player);
        long now = Util.getMillis();
        int rebuilds = 0;
        List<Section> visible = new ArrayList<>();
        for (Section section : SECTIONS.values()) {
            if (section.bounds == null) {
                section.bounds = bounds(section);
            }
            if (section.bounds == null || !frustum.isVisible(section.bounds)) {
                continue;
            }
            if ((section.dirty || now - section.builtAt > LIGHT_REFRESH_MS)
                    && (rebuilds < MAX_REBUILDS_PER_FRAME || section.builtAt == 0)) {
                build(section, level, now);
                rebuilds++;
            }
            if (section.buffer != null || (showHidden && section.hiddenBuffer != null)) {
                visible.add(section);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        RenderType renderType = RenderType.solid();
        renderType.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            renderType.clearRenderState();
            return;
        }
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, event.getModelViewMatrix(), event.getProjectionMatrix(), mc.getWindow());
        shader.apply();
        Uniform chunkOffset = shader.CHUNK_OFFSET;
        for (Section section : visible) {
            if (chunkOffset != null) {
                chunkOffset.set((float) (section.origin.getX() - camera.x),
                        (float) (section.origin.getY() - camera.y),
                        (float) (section.origin.getZ() - camera.z));
                chunkOffset.upload();
            }
            for (VertexBuffer buffer : new VertexBuffer[]{section.buffer, showHidden ? section.hiddenBuffer : null}) {
                if (buffer != null) {
                    buffer.bind();
                    buffer.draw();
                }
            }
        }
        if (chunkOffset != null) {
            chunkOffset.set(0f, 0f, 0f);
        }
        shader.clear();
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    private static AABB bounds(Section section) {
        AABB bounds = null;
        for (int id : section.wires) {
            SurfaceWire wire = WIRES.get(id);
            if (wire != null) {
                bounds = bounds == null ? wire.bounds() : bounds.minmax(wire.bounds());
            }
        }
        return bounds;
    }

    private static void build(Section section, ClientLevel level, long now) {
        section.dirty = false;
        section.builtAt = now;
        section.buffer = mesh(section, level, false, section.buffer);
        section.hiddenBuffer = mesh(section, level, true, section.hiddenBuffer);
    }

    /** Meshes the visible or the hidden wires of a section into {@code buffer}; null if there are none. */
    private static VertexBuffer mesh(Section section, ClientLevel level, boolean hidden, VertexBuffer buffer) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        var atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        for (int id : section.wires) {
            SurfaceWire wire = WIRES.get(id);
            if (wire == null || wire.material().hidden() != hidden) {
                continue;
            }
            Map<GridPos, Integer> owners = owners(wire);
            TextureAtlasSprite sprite = atlas.getSprite(wire.material().texture());
            int h = wire.material().halfThickness();
            List<GridPos> points = wire.points();
            int last = points.size() - 1;

            // one cube per point (corner / end); a shared free end is drawn by one wire only
            for (int i = 0; i <= last; i++) {
                GridPos p = points.get(i);
                boolean end = i == 0 || i == last;
                if (end && owners.getOrDefault(p, id) != id) {
                    continue;
                }
                box(builder, level, section.origin, sprite,
                        p.x() - h, p.y() - h, p.z() - h, p.x() + h, p.y() + h, p.z() + h, null);
            }
            // straight pieces between the cubes, without end caps -> nothing overlaps
            for (int i = 1; i <= last; i++) {
                addSegment(builder, level, section.origin, points.get(i - 1), points.get(i), h, sprite);
            }
        }
        MeshData mesh = builder.build();
        if (mesh == null) {
            if (buffer != null) {
                buffer.close();
            }
            return null;
        }
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }

    /** Piece between two cubes, split at block borders so every part gets the light of its own block. */
    private static void addSegment(BufferBuilder builder, ClientLevel level, BlockPos origin,
                                   GridPos a, GridPos b, int h, TextureAtlasSprite sprite) {
        Direction.Axis axis = a.x() != b.x() ? Direction.Axis.X : a.y() != b.y() ? Direction.Axis.Y : Direction.Axis.Z;
        int lo = Math.min(a.get(axis), b.get(axis)) + h;
        int hi = Math.max(a.get(axis), b.get(axis)) - h;
        int ai = axis.ordinal();
        int from = lo;
        while (from < hi) {
            int to = Math.min(hi, Math.floorDiv(from, GridPos.SCALE) * GridPos.SCALE + GridPos.SCALE);
            int[] min = {a.x() - h, a.y() - h, a.z() - h};
            int[] max = {a.x() + h, a.y() + h, a.z() + h};
            min[ai] = from;
            max[ai] = to;
            box(builder, level, origin, sprite, min[0], min[1], min[2], max[0], max[1], max[2], axis);
            from = to;
        }
    }

    /**
     * Axis aligned box in grid units. Faces on {@code skipAxis} are left out. UVs follow the world pixel grid
     * like a normal block, so the texture is never stretched.
     */
    private static void box(VertexConsumer c, ClientLevel level, BlockPos origin, TextureAtlasSprite sprite,
                            int gx0, int gy0, int gz0, int gx1, int gy1, int gz1, Direction.Axis skipAxis) {
        float s = GridPos.SCALE;
        BlockPos base = new BlockPos(Math.floorDiv(gx0, GridPos.SCALE), Math.floorDiv(gy0, GridPos.SCALE), Math.floorDiv(gz0, GridPos.SCALE));
        BlockPos center = new BlockPos(Math.floorDiv(gx0 + gx1, 2 * GridPos.SCALE),
                Math.floorDiv(gy0 + gy1, 2 * GridPos.SCALE), Math.floorDiv(gz0 + gz1, 2 * GridPos.SCALE));
        int light = LevelRenderer.getLightColor(level, center);

        float x0 = gx0 / s - origin.getX(), y0 = gy0 / s - origin.getY(), z0 = gz0 / s - origin.getZ();
        float x1 = gx1 / s - origin.getX(), y1 = gy1 / s - origin.getY(), z1 = gz1 / s - origin.getZ();
        // pixel coordinates inside the block of the box's lower corner
        float px0 = (gx0 - base.getX() * s) / 2f, py0 = (gy0 - base.getY() * s) / 2f, pz0 = (gz0 - base.getZ() * s) / 2f;
        float px1 = (gx1 - base.getX() * s) / 2f, py1 = (gy1 - base.getY() * s) / 2f, pz1 = (gz1 - base.getZ() * s) / 2f;

        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == skipAxis) {
                continue;
            }
            float[] uv = switch (dir) {
                case DOWN -> new float[]{px0, 16 - pz1, px1, 16 - pz0};
                case UP -> new float[]{px0, pz0, px1, pz1};
                case NORTH -> new float[]{16 - px1, 16 - py1, 16 - px0, 16 - py0};
                case SOUTH -> new float[]{px0, 16 - py1, px1, 16 - py0};
                case WEST -> new float[]{pz0, 16 - py1, pz1, 16 - py0};
                case EAST -> new float[]{16 - pz1, 16 - py1, 16 - pz0, 16 - py0};
            };
            quad(c, dir, x0, y0, z0, x1, y1, z1, sprite, light, uv);
        }
    }

    private static void quad(VertexConsumer c, Direction dir, float x0, float y0, float z0, float x1, float y1, float z1,
                             TextureAtlasSprite sprite, int light, float[] pixelUv) {
        float u0 = sprite.getU(clampPixel(pixelUv[0]) / 16f);
        float v0 = sprite.getV(clampPixel(pixelUv[1]) / 16f);
        float u1 = sprite.getU(clampPixel(pixelUv[2]) / 16f);
        float v1 = sprite.getV(clampPixel(pixelUv[3]) / 16f);
        int shade = (int) (SHADE[dir.ordinal()] * 255);
        int color = 0xFF000000 | shade << 16 | shade << 8 | shade;
        float nx = dir.getStepX(), ny = dir.getStepY(), nz = dir.getStepZ();
        float[][] v = switch (dir) {
            case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int i = 0; i < 4; i++) {
            c.addVertex(v[i][0], v[i][1], v[i][2])
                    .setColor(color)
                    .setUv(uv[i][0], uv[i][1])
                    .setLight(light)
                    .setNormal(nx, ny, nz);
        }
    }

    private static float clampPixel(float pixel) {
        return Math.max(0f, Math.min(16f, pixel));
    }

    // ------------------------------------------------------------------------------------------------
    // Placement preview
    // ------------------------------------------------------------------------------------------------

    private static void renderPreview(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        ItemStack stack = heldWire(player);
        WireStart start = stack.isEmpty() ? null : stack.get(EEDataComponents.WIRE_START.get());
        if (start == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
            lastPreviewText = "";
            return;
        }

        if (!(stack.getItem() instanceof SurfaceWireItem item)) {
            return;
        }
        SurfaceWirePlacement.Target target = SurfaceWirePlacement.resolve(
                level, hit.getBlockPos(), hit.getLocation(), hit.getDirection(), item.getMaterial());
        GridPos targetPos = target.node().isPresent() ? target.pos() : SurfaceWireItem.snapToFreeEnd(level, target.pos());
        SurfaceWireItem.Planned planned = SurfaceWireItem.plan(level, player, start, target, targetPos);
        List<GridPos> path = planned.points();
        double length = planned.length();
        int cost = planned.cost();
        boolean valid = path.size() > 1 && planned.problem() == null;
        int h = item.getMaterial().halfThickness();

        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugFilledBox());
        PoseStack pose = new PoseStack();
        float r = valid ? 0.4f : 1.0f, g = valid ? 1.0f : 0.35f, b = 0.4f;
        for (int i = 1; i < path.size(); i++) {
            AABB box = SurfaceWire.segmentBounds(path.get(i - 1), path.get(i), h, 0.004).move(camera.reverse());
            LevelRenderer.addChainedFilledBoxVertices(pose, consumer,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, 0.6f);
        }
        if (target.node().isPresent() || !targetPos.equals(target.pos())) {
            // snapped to a connector / wire end
            AABB marker = AABB.ofSize(targetPos.toVec3(), 0.15, 0.15, 0.15).move(camera.reverse());
            LevelRenderer.addChainedFilledBoxVertices(pose, consumer,
                    marker.minX, marker.minY, marker.minZ, marker.maxX, marker.maxY, marker.maxZ, 0.3f, 0.6f, 1.0f, 0.7f);
        }
        buffers.endBatch(RenderType.debugFilledBox());

        String text = String.format("%.1f/%d/%b/%s", length, cost, isAirMode(), planned.problem());
        if (!text.equals(lastPreviewText)) {
            lastPreviewText = text;
            Component message = Component.translatable("message.yesmenncreates.flat_wire.preview",
                    String.format("%.1f", length), cost);
            if (isAirMode()) {
                message = message.copy().append(" ").append(Component.translatable("message.yesmenncreates.flat_wire.air_mode"));
            }
            if (planned.problem() != null) {
                message = Component.translatable("message.yesmenncreates.flat_wire." + planned.problem())
                        .withStyle(net.minecraft.ChatFormatting.RED);
            }
            mc.gui.setOverlayMessage(message, false);
        }
    }

    private static ItemStack heldWire(Player player) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.getItem() instanceof SurfaceWireItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------------------------------------
    // Picking
    // ------------------------------------------------------------------------------------------------

    /** Id of the wire the player is looking at, or -1. */
    public static int lookedAtWire(Player player) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, hit.getLocation().distanceTo(eye) + 0.1);
        }
        Vec3 end = eye.add(player.getViewVector(1f).scale(reach));
        AABB ray = new AABB(eye, end);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        boolean showHidden = showHidden(player);
        for (SurfaceWire wire : WIRES.values()) {
            if ((wire.material().hidden() && !showHidden) || !wire.bounds().inflate(0.1).intersects(ray)) {
                continue;
            }
            List<GridPos> points = wire.points();
            for (int i = 1; i < points.size(); i++) {
                Optional<Vec3> clip = SurfaceWire.segmentBounds(points.get(i - 1), points.get(i),
                        wire.material().halfThickness(), 1 / 32d).clip(eye, end);
                if (clip.isPresent()) {
                    double d = clip.get().distanceToSqr(eye);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = wire.id();
                    }
                }
            }
        }
        return best;
    }
}
