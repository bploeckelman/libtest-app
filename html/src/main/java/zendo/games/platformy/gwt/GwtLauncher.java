package zendo.games.platformy.gwt;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import zendo.games.platformy.TestMain;
import zendo.games.platformy.config.Config;
import zendo.games.zenlib.App;

/** Launches the GWT application. */
public class GwtLauncher extends GwtApplication {
		@Override
		public GwtApplicationConfiguration getConfig () {
			// Resizable application, uses available space in browser
//			return new GwtApplicationConfiguration(true);
			// Fixed size application:
			return new GwtApplicationConfiguration(Config.window_width, Config.window_height);
		}

		@Override
		public ApplicationListener createApplicationListener () { 
			return new App(new TestMain());
		}
}
