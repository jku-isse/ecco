//package at.jku.isse.ecco.test;
//
//import javafx.application.Application;
//import javafx.stage.Stage;
//
//public final class Utility {
//
//	private Utility() {
//		throw new AssertionError("Class cannot be instantiated.");
//	}
//
//	public static void launchApp(AppLaunch appLaunch, String... sArArgs) {
//		DummyApplication.appLaunch = appLaunch;
//		DummyApplication.launch(DummyApplication.class, sArArgs);
//	}
//
//	// This must be public in order to instantiate successfully
//	public static class DummyApplication extends Application {
//
//		private static AppLaunch appLaunch;
//
//		@Override
//		public void start(Stage primaryStage) throws Exception {
//
//			if (appLaunch != null) {
//				appLaunch.start(this, primaryStage);
//			}
//		}
//
//		@Override
//		public void init() throws Exception {
//			if (appLaunch != null) {
//				appLaunch.init(this);
//			}
//		}
//
//		@Override
//		public void stop() throws Exception {
//			if (appLaunch != null) {
//				appLaunch.stop(this);
//			}
//		}
//	}
//
//	@FunctionalInterface
//	public static interface AppLaunch {
//		void start(Application app, Stage stage) throws Exception;
//
//		// Remove default keyword if you need to run in Java7 and below
//		default void init(Application app) throws Exception {
//		}
//
//		default void stop(Application app) throws Exception {
//		}
//	}
//
//}