package com.gtnewhorizons.quickbridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.File;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraft.client.gui.ScaledResolution;

@SideOnly(Side.CLIENT)
public class BridgingHandler {

    public static final BridgingHandler INSTANCE = new BridgingHandler();
    private boolean enabled = true;
    private int maxReach = 20;
    private int toggleKey = Keyboard.KEY_COMMA;
    private boolean keyDebounce = false;
    private boolean hasTarget = false;
    private int targetX, targetY, targetZ;
    private int targetSide = -1;
    private boolean rightClickDebounce = false;
    private Configuration config;
    private BridgingHandler() {}

    public static void init(File configFile) {
        INSTANCE.config = new Configuration(configFile);
        INSTANCE.loadConfig();
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    private float crossColorR = 0.0F, crossColorG = 1.0F, crossColorB = 1.0F;
    private float fillColorR = 0.0F, fillColorG = 0.6F, fillColorB = 1.0F;
    private float outlineAlpha = 0.4F;
    private boolean showOutlineAlways = true;
    private int crosshairStyle = 1; // 0=3D world, 1=2D HUD (reference style)

    private float crossSize = 1.0F; // multiplier
    private float crossThickness = 1.0F;

    private void loadConfig() {
        config.load();
        enabled = config.getBoolean("enabled", Configuration.CATEGORY_GENERAL, true, "Enable bridging assist");
        maxReach = config.getInt("maxReach", Configuration.CATEGORY_GENERAL, 20, 5, 50, "Maximum reach distance");
        crosshairStyle = config.getInt("crosshairStyle", Configuration.CATEGORY_GENERAL, 1, 0, 1,
            "0=3D world crosshair, 1=2D HUD indicator (matches reference)");
        crossSize = config.getFloat("crosshairSize", Configuration.CATEGORY_GENERAL, 1.0F, 0.5F, 2.0F,
            "Crosshair size multiplier (0.5-2.0)");
        crossThickness = config.getFloat("crosshairThickness", Configuration.CATEGORY_GENERAL, 1.0F, 0.5F, 2.0F,
            "Crosshair line thickness multiplier (0.5-2.0)");
        String crossColor = config.getString("crosshairColor", Configuration.CATEGORY_GENERAL, "0,255,255",
            "Crosshair RGB color (R,G,B)");
        String fillColor = config.getString("fillColor", Configuration.CATEGORY_GENERAL, "0,153,255",
            "Fill outline RGB color (R,G,B)");
        outlineAlpha = config.getFloat("outlineAlpha", Configuration.CATEGORY_GENERAL, 0.4F, 0.05F, 0.9F,
            "Outline fill opacity (0.05-0.9)");
        showOutlineAlways = config.getBoolean("showOutlineAlways", Configuration.CATEGORY_GENERAL, true,
            "Show outline even without F3");
        try {
            String[] c = crossColor.split(",");
            crossColorR = Integer.parseInt(c[0].trim()) / 255F;
            crossColorG = Integer.parseInt(c[1].trim()) / 255F;
            crossColorB = Integer.parseInt(c[2].trim()) / 255F;
        } catch (Exception e) { crossColorR=0; crossColorG=1; crossColorB=1; }
        try {
            String[] f = fillColor.split(",");
            fillColorR = Integer.parseInt(f[0].trim()) / 255F;
            fillColorG = Integer.parseInt(f[1].trim()) / 255F;
            fillColorB = Integer.parseInt(f[2].trim()) / 255F;
        } catch (Exception e) { fillColorR=0; fillColorG=0.6F; fillColorB=1; }
        if (config.hasChanged()) config.save();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (Keyboard.isKeyDown(toggleKey)) {
            if (!keyDebounce) {
                keyDebounce = true;
                enabled = !enabled;
                String msg = enabled ? "\u00a7a[\u73af\u7ed5\u653e\u7f6e] \u5df2\u5f00\u542f" : "\u00a7c[\u73af\u7ed5\u653e\u7f6e] \u5df2\u5173\u95ed";
                mc.thePlayer.addChatMessage(new ChatComponentText(msg));
            }
        } else { keyDebounce = false; }
        if (enabled) {
            computeBridgeTarget(mc);
        } else { hasTarget = false; targetSide = -1; }
        boolean rightDown = Mouse.isButtonDown(1);
        if (rightDown && !rightClickDebounce && enabled) tryPlaceBridgingBlock(mc);
        rightClickDebounce = rightDown;
    }
    private void computeBridgeTarget(Minecraft mc) {
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) { hasTarget = false; targetSide = -1; return; }
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) { hasTarget = false; targetSide = -1; return; }
        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = Vec3.createVectorHelper(start.xCoord + look.xCoord * maxReach, start.yCoord + look.yCoord * maxReach, start.zCoord + look.zCoord * maxReach);
        hasTarget = false; targetSide = -1;
        bresenhamScan(world, player, start, end, held);
    }
    private void bresenhamScan(World world, EntityPlayer player, Vec3 start, Vec3 end, ItemStack held) {
        double dx = end.xCoord - start.xCoord, dy = end.yCoord - start.yCoord, dz = end.zCoord - start.zCoord;
        int steps = (int) Math.ceil(Math.sqrt(dx*dx + dy*dy + dz*dz) * 2);
        if (steps < 1) steps = 1;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int bx = (int) Math.floor(start.xCoord + dx * t);
            int by = (int) Math.floor(start.yCoord + dy * t);
            int bz = (int) Math.floor(start.zCoord + dz * t);
            if (isPlayerStandingOn(player, bx, by, bz)) continue;
            Block block = world.getBlock(bx, by, bz);
            if (!block.isAir(world, bx, by, bz) && !block.isReplaceable(world, bx, by, bz)) continue;
            int[] sx = {0, 0, 1, -1, 0, 0};
            int[] sy = {1, -1, 0, 0, 0, 0};
            int[] sz = {0, 0, 0, 0, 1, -1};
            for (int s = 0; s < 6; s++) {
                int nx = bx + sx[s], ny = by + sy[s], nz = bz + sz[s];
                Block neighbor = world.getBlock(nx, ny, nz);
                if (neighbor.isOpaqueCube()) { targetX = bx; targetY = by; targetZ = bz; targetSide = s; hasTarget = true; return; }
            }
        }
    }
    private boolean isPlayerStandingOn(EntityPlayer player, int x, int y, int z) {
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        return (x == px && y == py && z == pz) || (x == px && y == py + 1 && z == pz);
    }
    private void tryPlaceBridgingBlock(Minecraft mc) {
        if (!hasTarget || targetSide < 0) return;
        EntityPlayer player = mc.thePlayer;
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;
        mc.playerController.onPlayerRightClick(player, mc.theWorld, held, targetX, targetY, targetZ, targetSide, Vec3.createVectorHelper(targetX + 0.5, targetY + 0.5, targetZ + 0.5));
        hasTarget = false; targetSide = -1;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!hasTarget || targetSide < 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * event.partialTicks;
        double py = mc.thePlayer.prevPosY + (mc.thePlayer.posY - mc.thePlayer.prevPosY) * event.partialTicks;
        double pz = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * event.partialTicks;
        double x = targetX + 0.5 - px;
        double y = targetY + 0.5 - py;
        double z = targetZ + 0.5 - pz;
        renderCrosshair(x, y, z, targetSide);
        if (showOutlineAlways || mc.gameSettings.showDebugInfo) {
            renderBridgingOutline(x - 0.5, y - 0.5, z - 0.5);
        }
    }

    private void renderCrosshair(double x, double y, double z, int side) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.5F);
        GL11.glColor4f(crossColorR, crossColorG, crossColorB, 0.85F);
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        float s = 0.35F;
        switch (side) {
            case 0: case 1:
                tess.addVertex(-s, 0, 0); tess.addVertex(s, 0, 0);
                tess.addVertex(0, 0, -s); tess.addVertex(0, 0, s); break;
            case 2: case 3:
                tess.addVertex(-s, 0, 0); tess.addVertex(s, 0, 0);
                tess.addVertex(0, -s, 0); tess.addVertex(0, s, 0); break;
            case 4: case 5:
                tess.addVertex(0, 0, -s); tess.addVertex(0, 0, s);
                tess.addVertex(0, -s, 0); tess.addVertex(0, s, 0); break;
        }
        // Center dot
        tess.addVertex(-0.03F, -0.03F, 0); tess.addVertex(0.03F, 0.03F, 0);
        tess.addVertex(-0.03F, 0.03F, 0); tess.addVertex(0.03F, -0.03F, 0);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
    }

    private void renderBridgingOutline(double x, double y, double z) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1);
        Tessellator tess = Tessellator.instance;

        // Filled faces (translucent)
        GL11.glDepthMask(false);
        tess.startDrawingQuads();
        tess.setColorRGBA_F(fillColorR, fillColorG, fillColorB, outlineAlpha);
        // Bottom
        tess.addVertex(box.minX, box.minY, box.minZ); tess.addVertex(box.maxX, box.minY, box.minZ);
        tess.addVertex(box.maxX, box.minY, box.maxZ); tess.addVertex(box.minX, box.minY, box.maxZ);
        // Top
        tess.addVertex(box.minX, box.maxY, box.minZ); tess.addVertex(box.minX, box.maxY, box.maxZ);
        tess.addVertex(box.maxX, box.maxY, box.maxZ); tess.addVertex(box.maxX, box.maxY, box.minZ);
        // North
        tess.addVertex(box.minX, box.minY, box.minZ); tess.addVertex(box.minX, box.maxY, box.minZ);
        tess.addVertex(box.maxX, box.maxY, box.minZ); tess.addVertex(box.maxX, box.minY, box.minZ);
        // South
        tess.addVertex(box.minX, box.minY, box.maxZ); tess.addVertex(box.maxX, box.minY, box.maxZ);
        tess.addVertex(box.maxX, box.maxY, box.maxZ); tess.addVertex(box.minX, box.maxY, box.maxZ);
        // West
        tess.addVertex(box.minX, box.minY, box.minZ); tess.addVertex(box.minX, box.minY, box.maxZ);
        tess.addVertex(box.minX, box.maxY, box.maxZ); tess.addVertex(box.minX, box.maxY, box.minZ);
        // East
        tess.addVertex(box.maxX, box.minY, box.minZ); tess.addVertex(box.maxX, box.maxY, box.minZ);
        tess.addVertex(box.maxX, box.maxY, box.maxZ); tess.addVertex(box.maxX, box.minY, box.maxZ);
        tess.draw();
        GL11.glDepthMask(true);

        // Edge outline (slightly brighter, more opaque)
        GL11.glLineWidth(2.0F);
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(fillColorR * 1.3F, fillColorG * 1.3F, fillColorB * 1.3F, outlineAlpha + 0.2F);
        // Bottom
        tess.addVertex(0,0,0); tess.addVertex(1,0,0); tess.addVertex(1,0,0); tess.addVertex(1,0,1);
        tess.addVertex(1,0,1); tess.addVertex(0,0,1); tess.addVertex(0,0,1); tess.addVertex(0,0,0);
        // Top
        tess.addVertex(0,1,0); tess.addVertex(1,1,0); tess.addVertex(1,1,0); tess.addVertex(1,1,1);
        tess.addVertex(1,1,1); tess.addVertex(0,1,1); tess.addVertex(0,1,1); tess.addVertex(0,1,0);
        // Vertical
        tess.addVertex(0,0,0); tess.addVertex(0,1,0); tess.addVertex(1,0,0); tess.addVertex(1,1,0);
        tess.addVertex(1,0,1); tess.addVertex(1,1,1); tess.addVertex(0,0,1); tess.addVertex(0,1,1);
        tess.draw();

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!hasTarget || targetSide < 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) return; // skip when F3 is open (avoids clutter)
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int w = res.getScaledWidth();
        int h = res.getScaledHeight();
        renderHudCrosshair(w, h, getCrosshairAlignment(targetSide));
    }

    private int getCrosshairAlignment(int side) {
        switch (side) {
            case 0: return -1; // DOWN (place below) → arrow up
            case 1: return 1;  // UP (place above) → arrow down
            default: return 0; // HORIZONTAL → left-right arrows
        }
    }

    private void renderHudCrosshair(int screenW, int screenH, int align) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        float s = crossSize;
        float lineW = 2.5F * crossThickness;
        Tessellator tess = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        if (align == 0) {
            // HORIZONTAL: filled arrows + thick ring
            int sp = (int)(12 * s);
            int arrowLen = (int)(8 * s);
            int halfW = (int)(5 * s);
            int ringR = (int)(10 * s);

            // Outer glow (semi-transparent thick lines behind)
            GL11.glLineWidth(lineW * 3);
            tess.startDrawing(GL11.GL_LINES);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.25F);
            // Glow left arrow
            tess.addVertex(cx - sp - arrowLen, cy, 0); tess.addVertex(cx - sp, cy - halfW, 0);
            tess.addVertex(cx - sp - arrowLen, cy, 0); tess.addVertex(cx - sp, cy + halfW, 0);
            // Glow right arrow
            tess.addVertex(cx + sp + arrowLen, cy, 0); tess.addVertex(cx + sp, cy - halfW, 0);
            tess.addVertex(cx + sp + arrowLen, cy, 0); tess.addVertex(cx + sp, cy + halfW, 0);
            tess.draw();

            // Filled arrow heads (GL_TRIANGLES)
            tess.startDrawing(GL11.GL_TRIANGLES);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.85F);
            // Left arrow head
            tess.addVertex(cx - sp - arrowLen, cy, 0);
            tess.addVertex(cx - sp + 2, cy - halfW, 0);
            tess.addVertex(cx - sp + 2, cy + halfW, 0);
            // Right arrow head
            tess.addVertex(cx + sp + arrowLen, cy, 0);
            tess.addVertex(cx + sp - 2, cy - halfW, 0);
            tess.addVertex(cx + sp - 2, cy + halfW, 0);
            tess.draw();

            // Shaft lines (thicker)
            GL11.glLineWidth(lineW);
            tess.startDrawing(GL11.GL_LINES);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.85F);
            tess.addVertex(cx - sp + 2, cy, 0); tess.addVertex(cx + sp - 2, cy, 0);
            tess.addVertex(cx - sp + 2, cy - halfW / 2, 0); tess.addVertex(cx - sp + 2, cy + halfW / 2, 0);
            tess.addVertex(cx + sp - 2, cy - halfW / 2, 0); tess.addVertex(cx + sp - 2, cy + halfW / 2, 0);
            tess.draw();

            // Thick ring with glow
            GL11.glLineWidth(lineW * 3);
            tess.startDrawing(GL11.GL_LINE_LOOP);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.2F);
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                tess.addVertex(cx + Math.cos(a) * (ringR + 2), cy + Math.sin(a) * (ringR + 2), 0);
            }
            tess.draw();

            GL11.glLineWidth(lineW + 0.5F);
            tess.startDrawing(GL11.GL_LINE_LOOP);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.75F);
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                tess.addVertex(cx + Math.cos(a) * ringR, cy + Math.sin(a) * ringR, 0);
            }
            tess.draw();
        } else {
            // UP/DOWN: filled triangle arrow
            int dir = align;
            float arrowLen = 13 * s;
            float halfW = 6 * s;

            // Outer glow
            GL11.glLineWidth(lineW * 3);
            tess.startDrawing(GL11.GL_LINES);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.25F);
            tess.addVertex(cx, cy - dir * arrowLen, 0); tess.addVertex(cx - halfW, cy - dir * (arrowLen - 5), 0);
            tess.addVertex(cx, cy - dir * arrowLen, 0); tess.addVertex(cx + halfW, cy - dir * (arrowLen - 5), 0);
            tess.addVertex(cx, cy - dir * arrowLen, 0); tess.addVertex(cx, cy + dir * 3, 0);
            tess.draw();

            // Filled arrow (GL_TRIANGLES)
            tess.startDrawing(GL11.GL_TRIANGLES);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.85F);
            tess.addVertex(cx, cy - dir * arrowLen, 0);
            tess.addVertex(cx - halfW, cy - dir * (arrowLen - 5), 0);
            tess.addVertex(cx + halfW, cy - dir * (arrowLen - 5), 0);
            tess.draw();

            // Shaft line
            GL11.glLineWidth(lineW);
            tess.startDrawing(GL11.GL_LINES);
            tess.setColorRGBA_F(crossColorR, crossColorG, crossColorB, 0.85F);
            tess.addVertex(cx, cy - dir * (arrowLen - 5), 0); tess.addVertex(cx, cy + dir * 3, 0);
            tess.draw();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }


}
