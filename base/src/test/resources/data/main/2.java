{
        try {
            //#if defined(LOGGING)
            //@#$LPS-LOGGING:GranularityType:Statement
            //@#$LPS-LOGGING:Localization:StartMethod
            LOG.info("ArgoUML Started.");
            //#endif
            SimpleTimer st = new SimpleTimer();
            st.mark("begin");

            initPreinitialize();
            
            st.mark("arguments");
            parseCommandLine(args);

            // Register our last chance exception handler
            AwtExceptionHandler.registerExceptionHandler();
            
            // Get the splash screen up as early as possible
            st.mark("create splash");
            SplashScreen splash = null;
            if (!batch) {
                // We have to do this to set the LAF for the splash screen
                st.mark("initialize laf");
                LookAndFeelMgr.getInstance().initializeLookAndFeel();
                if (theTheme != null) {
                    LookAndFeelMgr.getInstance().setCurrentTheme(theTheme);
                }
                if (doSplash) {
                    splash = initializeSplash();
                }
            }

            // main initialization happens here
            ProjectBrowser pb = initializeSubsystems(st, splash);

            // Needs to happen after initialization is done & modules loaded
            st.mark("perform commands");
            if (batch) {
                // TODO: Add an "open most recent project" command so that 
                // command state can be decoupled from user settings?
                performCommandsInternal(commands);
                commands = null;

                System.out.println("Exiting because we are running in batch.");
                new ActionExit().doCommand(null);
                return;
            }
            
            if (reloadRecent && projectName == null) {
                projectName = getMostRecentProject();
            }

            URL urlToOpen = null;
            if (projectName != null) {
                projectName =
                    PersistenceManager.getInstance().fixExtension(projectName);
                urlToOpen = projectUrl(projectName, urlToOpen);
            }

            openProject(st, splash, pb, urlToOpen);

            st.mark("perspectives");
            if (splash != null) {
                splash.getStatusBar().showProgress(75);
            }

            st.mark("open window");
            updateProgress(splash, 95, "statusmsg.bar.open-project-browser");
            ArgoFrame.getInstance().setVisible(true);

            st.mark("close splash");
            if (splash != null) {
                splash.setVisible(false);
                splash.dispose();
                splash = null;
            }

            performCommands(commands);
            commands = null;
            //#if defined(COGNITIVE)
            //@#$LPS-COGNITIVE:GranularityType:Statement
            st.mark("start critics");
            Runnable startCritics = new StartCritics();
            Main.addPostLoadAction(startCritics);
            //#endif
            st.mark("start loading modules");
            Runnable moduleLoader = new LoadModules();
            Main.addPostLoadAction(moduleLoader);

            PostLoad pl = new PostLoad(postLoadActions);
            Thread postLoadThead = new Thread(pl);
            postLoadThead.start();
            //#if defined(LOGGING)
            //@#$LPS-LOGGING:GranularityType:Statement
            LOG.info("");
            LOG.info("profile of load time ############");
            for (Enumeration i = st.result(); i.hasMoreElements();) {
                LOG.info(i.nextElement());
            }
            LOG.info("#################################");
            LOG.info("");
            //#endif
            st = null;
            ArgoFrame.getInstance().setCursor(
                    Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            //ToolTipManager.sharedInstance().setInitialDelay(500);
            ToolTipManager.sharedInstance().setDismissDelay(50000000);
        } catch (Throwable t) {
            //#if defined(LOGGING)
            try {                
                //@#$LPS-LOGGING:GranularityType:Statement
                LOG.fatal("Fatal error on startup.  ArgoUML failed to start", 
                        t);
            } finally {
            //#endif
                System.out.println("Fatal error on startup.  "
                        + "ArgoUML failed to start.");
                t.printStackTrace();
                System.exit(1);
            //#if defined(LOGGING)
            }
            //#endif
        }
}