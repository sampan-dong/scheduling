/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package org.ow2.proactive.resourcemanager.gui.data;

import java.security.KeyException;

import javax.security.auth.login.LoginException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.node.NodeException;
import org.ow2.proactive.authentication.crypto.CredData;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.gui.common.ActiveObjectPingerThread;
import org.ow2.proactive.resourcemanager.Activator;
import org.ow2.proactive.resourcemanager.authentication.RMAuthentication;
import org.ow2.proactive.resourcemanager.common.RMConstants;
import org.ow2.proactive.resourcemanager.common.event.RMInitialState;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.frontend.RMConnection;
import org.ow2.proactive.resourcemanager.frontend.RMEventListener;
import org.ow2.proactive.resourcemanager.frontend.RMMonitoring;
import org.ow2.proactive.resourcemanager.gui.actions.JMXActionsManager;
import org.ow2.proactive.resourcemanager.gui.data.model.RMModel;
import org.ow2.proactive.resourcemanager.gui.dialog.SelectResourceManagerDialog;
import org.ow2.proactive.resourcemanager.gui.views.ResourceExplorerView;
import org.ow2.proactive.resourcemanager.gui.views.ResourcesCompactView;
import org.ow2.proactive.resourcemanager.gui.views.ResourcesTabView;
import org.ow2.proactive.resourcemanager.gui.views.ResourcesTopologyView;
import org.ow2.proactive.resourcemanager.gui.views.StatisticsView;


/**
 * @author The ProActive Team
 *
 */
public class RMStore implements ActiveObjectPingerThread.PingListener {

    private static RMStore instance;
    private static boolean isConnected;

    private ResourceManagerProxy resourceManagerAO;

    private RMMonitoring rmMonitoring;
    private EventsReceiver receiver;
    private RMModel model;
    private String baseURL;

    private ActiveObjectPingerThread pinger;

    private RMStore(String url, String login, String password, byte[] cred) throws RMException {
        try {
            resourceManagerAO = ResourceManagerProxy.getProxyInstance();
            baseURL = url;

            if (url != null && !url.endsWith("/")) {
                url += "/";
            }

            RMAuthentication rmAuthentication;
            try {
                System.out.println("Joining resource manager on the following url " + url +
                    RMConstants.NAME_ACTIVE_OBJECT_RMAUTHENTICATION);
                rmAuthentication = RMConnection.join(url + RMConstants.NAME_ACTIVE_OBJECT_RMAUTHENTICATION);
            } catch (RMException e) {
                throw new RMException("Resource manager does not exist on the following url: " + url, e);
            }

            Credentials credentials;
            try {
                if (cred != null) {
                    try {
                        credentials = Credentials.getCredentialsBase64(cred);
                    } catch (KeyException e) {
                        Activator.log(IStatus.INFO, "Failed to read credentials", e);
                        throw new RMException("Failed to read credentials", e);
                    }
                } else {
                    try {
                        credentials = Credentials.createCredentials(new CredData(CredData.parseLogin(login),
                            CredData.parseDomain(login), password), rmAuthentication.getPublicKey());
                    } catch (KeyException e) {
                        throw new LoginException("Could not create encrypted credentials: " + e.getMessage());
                    }
                }
                resourceManagerAO.connect(rmAuthentication, credentials);
                //resourceManager = auth.login(creds);
            } catch (Exception e) {
                Activator.log(IStatus.INFO, "Login exception for user " + login, e);
                throw new RMException(e.getMessage());
            }

            rmMonitoring = resourceManagerAO.syncGetMonitoring();

            instance = this;
            receiver = PAActiveObject.newActive(EventsReceiver.class, new Object[] {});
            RMInitialState initialState = resourceManagerAO
                    .syncAddRMEventListener((RMEventListener) receiver);
            RMStore.setConnected(true);

            receiver.init(initialState);
            SelectResourceManagerDialog.saveInformations();

            startPinger(resourceManagerAO);
            isConnected = true;
            //ControllerView.getInstance().connectedEvent(isAdmin);

            // Initialize the JMX chartit action
            JMXActionsManager.getInstance().initJMXClient(url, rmAuthentication,
                    new Object[] { login, credentials });

        } catch (ActiveObjectCreationException e) {
            Activator.log(IStatus.ERROR, "Exception when creating active object", e);
            e.printStackTrace();
            throw new RMException(e.getMessage(), e);
        } catch (NodeException e) {
            Activator.log(IStatus.ERROR, "Node exeption", e);
            e.printStackTrace();
            throw new RMException(e.getMessage(), e);
        } catch (Exception e) {
            Activator.log(IStatus.ERROR, "Connection exeption", e);
            e.printStackTrace();
            throw new RMException(e.getMessage(), e);
        }
    }

    @Override
    public void onPingError() {
        RMStore.getInstance().shutDownActions(true);
    }

    @Override
    public void onPingFalse() {
        onPingError();
    }

    @Override
    public void onPingTimeout() {
        onPingError();
    }

    private void startPinger(ResourceManagerProxy proxy) {
        pinger = new ActiveObjectPingerThread(proxy, this);
        pinger.setName("Pinger");
        pinger.start();
    }

    public static void newInstance(String url, String login, String password, byte[] cred)
            throws RMException, SecurityException {
        instance = new RMStore(url, login, password, cred);
    }

    public static RMStore getInstance() {
        return instance;
    }

    public static void setConnected(boolean con) {
        isConnected = con;
    }

    public static boolean isConnected() {
        return isConnected;
    }

    public RMModel getModel() {
        if (model == null) {
            model = new RMModel();
        }
        return model;
    }

    /**
     * To get the rmAdmin
     *
     * @return the rmAdmin
     * @throws RMException
     */
    public ResourceManagerProxy getResourceManager() {
        return resourceManagerAO;
    }

    /**
     * To get the rmMonitoring
     *
     * @return the rmMonitoring
     */
    public RMMonitoring getRMMonitoring() {
        return rmMonitoring;
    }

    public String getURL() {
        return this.baseURL;
    }

    /*
     * method is synchronized since it can be concurrently called by the RMEventListener
     * and pinger thread
     */
    public synchronized void shutDownActions(final boolean failed) {
        if (isConnected) {
            isConnected = false;
            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    String msg;
                    if (failed) {
                        msg = "seems to be down";
                    } else {
                        msg = "has been shutdown";
                    }
                    MessageDialog.openInformation(Display.getDefault().getActiveShell(), "shutdown",
                            "Resource manager  '" + RMStore.getInstance().getURL() + "'  " + msg +
                                ", now disconnect.");
                    disconnectionActions();
                }
            });
        }
    }

    /**
     * Clean displayed view ; detach them from model,
     * set status to disconnect. Terminates EventsReceiver AO
     *
     */
    public void disconnectionActions() {
        pinger.stopPinger();

        // remove empty editor if it exists
        try {
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().setEditorAreaVisible(false);
        } catch (Throwable t) {
        }

        //clear Tab view if tab panel is displayed
        if (ResourcesTabView.getTabViewer() != null) {
            ResourcesTabView.getTabViewer().setInput(null);
        }
        //clear Tree view if tree panel is displayed
        if (ResourceExplorerView.getTreeViewer() != null) {
            ResourceExplorerView.getTreeViewer().setInput(null);
            //ResourceExplorerView.init();
        }
        if (ResourcesCompactView.getCompactViewer() != null) {
            ResourcesCompactView.getCompactViewer().clear();
        }
        //clear stats view if stats panel is displayed
        if (StatisticsView.getNodesStatsViewer() != null) {
            StatisticsView.getNodesStatsViewer().setInput(null);
            StatisticsView.getHostsStatsViewer().setInput(null);
        }
        //clear topology view if topology panel is displayed
        if (ResourcesTopologyView.getTopologyViewer() != null) {
            ResourcesTopologyView.getTopologyViewer().clear();
        }

        // Disconnect JMX ChartIt action
        JMXActionsManager.getInstance().disconnectJMXClient();
        resourceManagerAO.disconnect();
        resourceManagerAO = null;
        rmMonitoring = null;
        model = null;
        baseURL = null;
        isConnected = false;
        // termination is asynchronous since it is called from GUI thread
        PAActiveObject.terminateActiveObject(receiver, false);
        RMStatusBarItem.getInstance().setText("disconnected");
    }

}
