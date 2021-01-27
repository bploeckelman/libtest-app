package zendo.games.platformy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import zendo.games.zenlib.assets.Content;

public class Assets extends Content {

    public static BitmapFont font;
    public static TiledMap tiledMap;
    public static Texture pixel;
    private static TextureAtlas atlas;

    public static void load() {
        font = new BitmapFont();
        tiledMap = new TmxMapLoader().load("maps/room_0x0.tmx");
        pixel = new Texture("pixel.png");
        pixel.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // load sprites
        atlas = new TextureAtlas("atlas/sprites.atlas");
        sprites.addAll(
                  Content.loadSprite("sprites/player.json", atlas)
                , Content.loadSprite("sprites/blob.json", atlas)
                , Content.loadSprite("sprites/pop.json", atlas)
        );
    }

    public static void unload() {
        Content.unload();

        tiledMap.dispose();
        atlas.dispose();
        pixel.dispose();
        font.dispose();
    }

}
