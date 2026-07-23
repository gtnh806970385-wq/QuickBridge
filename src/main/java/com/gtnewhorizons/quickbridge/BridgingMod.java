package com.gtnewhorizons.quickbridge;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = BridgingMod.MODID, version = BridgingMod.VERSION, name = "简易快速搭建 (QuickBridge)")
public class BridgingMod {

    public static final String MODID = "quickbridge";
    public static final String VERSION = "0.2.3";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BridgingHandler.init(event.getSuggestedConfigurationFile());
    }
}
