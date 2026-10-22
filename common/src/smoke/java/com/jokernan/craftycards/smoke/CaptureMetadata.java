package com.jokernan.craftycards.smoke;

import net.minecraft.client.Minecraft;
import java.nio.file.*;

/** Records the effective GUI size: requested GUI scale may be capped by vanilla. */
final class CaptureMetadata {
    private CaptureMetadata() {}
    static void write(Minecraft mc,String name) {
        var w=mc.getWindow();
        String json="{\"windowWidth\":"+w.getWidth()+",\"windowHeight\":"+w.getHeight()
            +",\"guiWidth\":"+w.getGuiScaledWidth()+",\"guiHeight\":"+w.getGuiScaledHeight()
            +",\"guiScale\":"+w.getGuiScale()+",\"requestedScale\":"+mc.options.guiScale().get()+"}";
        try {
            Path directory=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
            Files.writeString(directory.resolve(name+".json"),json);
        }catch(Exception e){throw new RuntimeException(e);}
    }
}
