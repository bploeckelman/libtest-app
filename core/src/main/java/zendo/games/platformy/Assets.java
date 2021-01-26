package zendo.games.platformy;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import zendo.games.zenlib.assets.Content;
import zendo.games.zenlib.assets.Sprite;
import zendo.games.zenlib.utils.Aseprite;

public class Assets extends Content {

    private static final boolean output_aseprite_atlas_as_png = false;

    public static BitmapFont font;
    public static TiledMap tiledMap;
    public static Texture pixel;
    private static TextureAtlas atlas;

    public static void load() {
        font = new BitmapFont();
        tiledMap = new TmxMapLoader().load("maps/room_0x0.tmx");
        pixel = new Texture("pixel.png");
        pixel.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // create a pixmap packer to generate a texture atlas from aseprite frames
        int pageWidth = 1024;
        int pageHeight = 1024;
        Pixmap.Format pageFormat = Pixmap.Format.RGBA8888;
        int padding = 0;
        boolean duplicateBorder = false;
        boolean stripWhitespaceX = false;
        boolean stripWhitespaceY = false;
        PixmapPacker.PackStrategy packStrategy = new PixmapPacker.GuillotineStrategy();
        PixmapPacker packer = new PixmapPacker(
                pageWidth, pageHeight, pageFormat, padding,
                duplicateBorder, stripWhitespaceX, stripWhitespaceY,
                packStrategy);

        // load aseprite files and pack animation frame pixmaps
        Aseprite.SpriteInfo playerSpriteInfo = Aseprite.loadAndPack(packer, "sprites/player.ase");
        Aseprite.SpriteInfo blobSpriteInfo = Aseprite.loadAndPack(packer, "sprites/blob.ase");
        Aseprite.SpriteInfo popSpriteInfo = Aseprite.loadAndPack(packer, "sprites/pop.ase");

        // create texture atlas from packer
        Texture.TextureFilter filter = Texture.TextureFilter.Nearest;
        boolean useMipMaps = false;
        atlas = packer.generateTextureAtlas(filter, filter, useMipMaps);

        // cleanup packer
        packer.dispose();

        // create sprites (and dispose pixmaps in SpriteInfo.Aseprite)
        Sprite player = Aseprite.createSprite(playerSpriteInfo, atlas);
        Sprite blob = Aseprite.createSprite(blobSpriteInfo, atlas);
        Sprite pop = Aseprite.createSprite(popSpriteInfo, atlas);

        // save the loaded sprites
        sprites.addAll(
                  player
                , blob
                , pop
        );

        // write out the aseprite texture atlas for debugging purposes if so desired
//        if (output_aseprite_atlas_as_png) {
//            Texture atlas_texture = atlas.getTextures().first();
//            Pixmap atlas_pixmap = atlas_texture.getTextureData().consumePixmap();
//            FileHandle file = new FileHandle("aseprite_atlas.png");
//            PixmapIO.writePNG(file, atlas_pixmap);
//            atlas_pixmap.dispose();
//        }
    }

    public static void unload() {
        Content.unload();

        tiledMap.dispose();
        atlas.dispose();
        pixel.dispose();
        font.dispose();
    }

}
