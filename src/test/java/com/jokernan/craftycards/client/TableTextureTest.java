package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class TableTextureTest {
    private static final Path ASSETS=Path.of("common/src/main/resources/assets/crafty_cards");
    private static String text(String path)throws Exception{return Files.readString(ASSETS.resolve(path));}
    private static List<String> values(String json,String key) {
        var matches=Pattern.compile("\""+key+"\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        var values=new ArrayList<String>();while(matches.find())values.add(matches.group(1));return values;
    }
    private static String atlas(String model)throws Exception {
        String value=text("models/"+model+".json");
        var atlases=values(value,"atlas");if(!atlases.isEmpty())return atlases.getFirst();
        var parents=values(value,"parent");assertFalse(parents.isEmpty(),"model needs an atlas or parent: "+model);
        return atlas(parents.getFirst().replace("crafty_cards:",""));
    }
    @Test void worldModelsUseIndependentGameAtlases()throws Exception {
        for(String model:values(text("blockstates/ddz_table.json"),"model")) {
            assertEquals("crafty_cards:block/ddz_table_atlas",atlas(model.replace("crafty_cards:","")));
        }
        String zjh=values(text("blockstates/zjh_table.json"),"model").getFirst().replace("crafty_cards:","");
        assertEquals("crafty_cards:block/zjh_table_atlas",atlas(zjh),"炸金花世界方块不能再复用斗地主贴图");
    }
    @Test void inventoryModelsMatchTheirOwnWorldTexture()throws Exception {
        assertEquals("crafty_cards:block/ddz_table_atlas",atlas("item/ddz_table"));
        assertEquals("crafty_cards:block/zjh_table_atlas",atlas("item/zjh_table"));
    }
    @Test void clothIsBlueForDdzAndBurgundyForZjh()throws Exception {
        for(String game:List.of("ddz","zjh")) {
            var image=ImageIO.read(ASSETS.resolve("textures/block/"+game+"_table_atlas.png").toFile());
            assertNotNull(image);assertEquals(32,image.getWidth());assertEquals(32,image.getHeight());
            long red=0,green=0,blue=0;
            for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                int rgb=image.getRGB(x,y);assertEquals(255,rgb>>>24,"方块图集不能有透明缝");
                if(x>=16&&y<16){red+=(rgb>>16)&255;green+=(rgb>>8)&255;blue+=rgb&255;}
            }
            if(game.equals("ddz"))assertTrue(blue>red*1.3&&blue>green,"斗地主桌布必须为蓝色");
            else assertTrue(red>green*1.3&&red>blue*1.2,"炸金花桌布必须为酒红色");
        }
    }
}
