package org.apache.catalina.startup;

import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class MyTestLifecycleListener implements LifecycleListener {


    private static final Log log = LogFactory.getLog(VersionLoggerListener.class);



    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        log.info("MyTestLifecycleListener type = " + event.getType() + ", data = " + event.getData());
    }
}
