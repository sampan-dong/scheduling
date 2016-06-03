/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2015 INRIA/University of
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
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.resourcemanager.rmnode;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.descriptor.data.VirtualNode;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.ow2.proactive.authentication.principals.UserNamePrincipal;
import org.ow2.proactive.jmx.naming.JMXTransportProtocol;
import org.ow2.proactive.permissions.PrincipalPermission;
import org.ow2.proactive.resourcemanager.authentication.Client;
import org.ow2.proactive.resourcemanager.common.NodeState;
import org.ow2.proactive.resourcemanager.common.event.RMEventType;
import org.ow2.proactive.resourcemanager.common.event.RMNodeDescriptor;
import org.ow2.proactive.resourcemanager.common.event.RMNodeEvent;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.ow2.proactive.scripting.Script;
import org.ow2.proactive.scripting.ScriptHandler;
import org.ow2.proactive.scripting.ScriptLoader;
import org.ow2.proactive.scripting.ScriptResult;
import org.ow2.proactive.scripting.SelectionScript;

import java.io.IOException;
import java.io.Serializable;
import java.security.Permission;
import java.util.HashMap;
import java.util.Map;


/**
 * Implementation of the RMNode Interface.
 * An RMNode is a ProActive node able to execute schedulers tasks.
 * So an RMNode is a representation of a ProActive node object with its associated {@link NodeSource},
 * and its state in the Resource Manager :<BR>
 * -free : node is ready to perform a task.<BR>
 * -busy : node is executing a task.<BR>
 * -to be released : node is busy and have to be removed at the end of the its current task.<BR>
 * -down : node is broken, and not anymore able to perform tasks.<BR><BR>
 *
 * Resource Manager can select nodes that verify criteria. this selection is implemented with
 * {@link SelectionScript} objects. Each node memorize results of executed scripts, in order to
 * answer faster to a selection already asked.
 *
 * @see NodeSource
 * @see SelectionScript
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 *
 */
public class RMNodeImpl implements RMNode, Serializable {

    private final static Logger logger = Logger.getLogger(RMNodeImpl.class);

    /** HashMap associates a selection Script to its result on the node */
    private HashMap<SelectionScript, Integer> scriptStatus;

    /** ProActive Node Object of the RMNode */
    private Node node;

    /** URL of the node, considered as its unique ID */
    private String nodeURL;

    /** Name of the node */
    private String nodeName;

    /** {@link VirtualNode} name of the node */
    private String vnodeName;

    /** Host name of the node */
    private String hostName;

    /** JVM name of the node */
    private String jvmName;

    /** Script handled, manage scripts launching and results recovering */
    private ScriptHandler handler = null;

    /** {@link NodeSource} Stub of NodeSource that handle the RMNode */
    private NodeSource nodeSource;

    /** {@link NodeSource} NodeSource that handle the RMNode */
    private String nodeSourceName;

    /** State of the node */
    private NodeState state;

    /** Time stamp of the latest state change */
    private long stateChangeTime;

    /** client registered the node in the resource manager */
    private Client provider;

    /** client taken the node for computations */
    private Client owner;

    /** Node access permission*/
    private Permission nodeAccessPermission;

    /** The add event */
    private RMNodeEvent addEvent;

    /** The last event */
    private RMNodeEvent lastEvent;

    private String[] jmxUrls;

    /** true if node is protected with token */
    private boolean protectedByToken = false;

    /** Create an RMNode Object.
     * A Created node begins to be free.
     * @param node ProActive node deployed.
     * @param nodeSource {@link VirtualNode} name of the node.
     * @param nodeSource {@link NodeSource} Stub of NodeSource that handle the RMNode.
     */
    public RMNodeImpl(Node node, NodeSource nodeSource, Client provider, Permission nodeAccessPermission) {
        this.node = node;
        this.nodeSource = nodeSource;
        this.nodeSourceName = nodeSource.getName();
        this.provider = provider;
        this.nodeAccessPermission = nodeAccessPermission;
        this.nodeName = node.getNodeInformation().getName();
        this.nodeURL = node.getNodeInformation().getURL();
        this.hostName = node.getNodeInformation().getVMInformation().getHostName();
        this.jvmName = node.getProActiveRuntime().getURL();
        this.scriptStatus = new HashMap<>();
        this.state = NodeState.FREE;
        this.stateChangeTime = System.currentTimeMillis();
        this.addEvent = null;
        this.lastEvent = null;
        this.jmxUrls = new String[JMXTransportProtocol.values().length];
    }

    /**
     * Returns the name of the node.
     * @return the name of the node.
     */
    public String getNodeName() {
        return this.nodeName;
    }

    /**
     * @see org.ow2.proactive.resourcemanager.rmnode.RMNode#getNode()
     */
    public Node getNode() {
        return this.node;
    }

    /**
     * Returns the Virtual node name of the RMNode.
     * @return the Virtual node name  of the RMNode.
     */
    public String getVNodeName() {
        return this.vnodeName;
    }

    /**
     * Returns the host name of the RMNode.
     * @return the host name of the RMNode.
     */
    public String getHostName() {
        return this.hostName;
    }

    /**
     * Returns the java virtual machine name of the RMNode.
     * @return the java virtual machine name of the RMNode.
     */
    public String getDescriptorVMName() {
        return this.jvmName;
    }

    /**
     * Returns the NodeSource name of the RMNode.
     * @return {@link NodeSource} name of the RMNode.
     */
    public String getNodeSourceName() {
        return this.nodeSourceName;
    }

    /**
     * Returns the unique id of the RMNode.
     * @return the unique id of the RMNode represented by its URL.
     */
    public String getNodeURL() {
        return nodeURL;
    }

    /**
     * Changes the state of this node to {@link NodeState#BUSY}.
     */
    public void setBusy(Client owner) {
        this.state = NodeState.BUSY;
        this.stateChangeTime = System.currentTimeMillis();
        this.owner = owner;
    }

    /**
     * Changes the state of this node to {@link NodeState#FREE}.
     */
    public void setFree() {
        this.state = NodeState.FREE;
        this.stateChangeTime = System.currentTimeMillis();
        this.owner = null;
    }

    /**
     * Changes the state of this node to {@link NodeState#CONFIGURING}
     */
    public void setConfiguring(Client owner) {
        if (!this.isDown()) {
            this.state = NodeState.CONFIGURING;
            this.stateChangeTime = System.currentTimeMillis();
        }
    }

    /**
     * Changes the state of this node to {@link NodeState#DOWN}.
     */
    public void setDown() {
        this.state = NodeState.DOWN;
        this.stateChangeTime = System.currentTimeMillis();
    }

    /**
     * Changes the state of this node to {@link NodeState#TO_BE_REMOVED}.
     */
    public void setToRemove() {
        this.state = NodeState.TO_BE_REMOVED;
        this.stateChangeTime = System.currentTimeMillis();
    }

    /**
     * @return true if the node is free, false otherwise.
     */
    public boolean isFree() {
        return this.state == NodeState.FREE;
    }

    /**
     * @return true if the node is busy, false otherwise.
     */
    public boolean isBusy() {
        return this.state == NodeState.BUSY;
    }

    /**
     * @return true if the node is down, false otherwise.
     */
    public boolean isDown() {
        return this.state == NodeState.DOWN;
    }

    /**
     * @return true if the node is 'to be released', false otherwise.
     */
    public boolean isToRemove() {
        return this.state == NodeState.TO_BE_REMOVED;
    }

    /**
     * @return true if the node is 'configuring', false otherwise.
     */
    public boolean isConfiguring() {
        return this.state == NodeState.CONFIGURING;
    }

    public boolean isLocked() {
        return this.state == NodeState.LOCKED;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(Client owner) {
        this.state = NodeState.LOCKED;
        this.stateChangeTime = System.currentTimeMillis();
        this.owner = owner;
    }

    /**
     * @return a String showing informations about the node.
     */
    public String getNodeInfo() {
        String newLine = System.lineSeparator();
        String nodeInfo = "Node " + nodeName + newLine;
        nodeInfo += "URL : " + nodeURL + newLine;
        nodeInfo += "Node source : " + nodeSourceName + newLine;
        nodeInfo += "Provider : " + provider.getName() + newLine;
        nodeInfo += "Used by : " + (owner == null ? "nobody" : owner.getName()) + newLine;
        nodeInfo += "State : " + state + newLine;
        nodeInfo += "JMX RMI: " + getJMXUrl(JMXTransportProtocol.RMI) + newLine;
        nodeInfo += "JMX RO: " + getJMXUrl(JMXTransportProtocol.RO) + newLine;
        return nodeInfo;
    }

    private void initHandler() throws NodeException {
        if (this.handler == null) {
            try {
                this.handler = ScriptLoader.createHandler(this.node);
            } catch (Exception e) {
                throw new NodeException("Unable to create Script Handler on node ", e);
            }
        }
    }

    /**
     * Returns the ProActive stub on the remote script handler.
     * @return the ProActive stub on the script handler.
     */
    public ScriptHandler getHandler() throws NodeException {
        this.initHandler();
        return this.handler;
    }

    /**
     * Execute a selection Script in order to test the Node.
     * If no script handler is defined, create one, and execute the script.
     * @param script Selection script to execute
     * @param bindings bindings to use to execute the selection scripts
     * @return Result of the test.
     *
     */
    public <T> ScriptResult<T> executeScript(Script<T> script, Map<String, Serializable> bindings) {
        try {
            this.initHandler();
        } catch (NodeException e) {
            return new ScriptResult<>(e);
        }
        if (bindings != null) {
            for (String key : bindings.keySet()) {
                this.handler.addBinding(key, bindings.get(key));
            }
        }
        return this.handler.handle(script);
    }

    /**
     * Clean the node.
     * kill all active objects on the node.
     * @throws NodeException
     */
    public synchronized void clean() throws NodeException {
        handler = null;
        try {
            logger.debug(nodeURL + " : cleaning");
            node.killAllActiveObjects();
        } catch (IOException e) {
            throw new NodeException("Node is down");
        }
    }

    /**
     * Compare two RMNode objects
     * @return true if the two RMNode objects represent the same Node.
     */
    @Override
    public boolean equals(Object imnode) {
        if (imnode instanceof RMNode) {
            return this.nodeURL.equals(((RMNode) imnode).getNodeURL());
        }

        return false;
    }

    /**
     * @return HashCode of node's ID,
     * i.e. the hashCode of the node's URL.
     */
    @Override
    public int hashCode() {
        return this.nodeURL.hashCode();
    }

    /**
     * Gives the HashMap of all scripts tested with corresponding results.
     * @return the HashMap of all scripts tested with corresponding results.
     */
    public HashMap<SelectionScript, Integer> getScriptStatus() {
        return this.scriptStatus;
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     * @param rmnode the RMNode object to compare
     * @return an integer
     */
    public int compareTo(RMNode rmnode) {
        if (this.getVNodeName().equals(rmnode.getVNodeName())) {
            if (this.getHostName().equals(rmnode.getHostName())) {
                if (this.getDescriptorVMName().equals(rmnode.getDescriptorVMName())) {
                    return this.getNodeURL().compareTo(rmnode.getNodeURL());
                } else {
                    return this.getDescriptorVMName().compareTo(rmnode.getDescriptorVMName());
                }
            } else {
                return this.getHostName().compareTo(rmnode.getHostName());
            }
        } else {
            return this.getVNodeName().compareTo(rmnode.getVNodeName());
        }
    }

    /**
     * @return the stub of the {@link NodeSource} that handle the RMNode.
     */
    public NodeSource getNodeSource() {
        return this.nodeSource;
    }

    /**
     * Set the NodeSource stub to the RMNode.
     * @param ns Stub of the NodeSource that handle the IMNode.
     */
    public void setNodeSource(NodeSource ns) {
        this.nodeSource = ns;
    }

    /**
     * @see org.ow2.proactive.resourcemanager.rmnode.RMNode#getState()
     */
    public NodeState getState() {
        return this.state;
    }

    /**
     * {@inheritDoc}
     */
    public Client getOwner() {
        return owner;
    }

    /**
     * {@inheritDoc}
     */
    public Client getProvider() {
        return provider;
    }

    /**
     * {@inheritDoc}
     */
    public Permission getUserPermission() {
        return nodeAccessPermission;
    }

    /**
     * {@inheritDoc}
     */
    public Permission getAdminPermission() {
        return new PrincipalPermission(provider.getName(), provider.getSubject().getPrincipals(
                UserNamePrincipal.class));
    }

    /**
     * {@inheritDoc}
     */
    public RMNodeEvent getAddEvent() {
        return this.addEvent;
    }

    /**
     * {@inheritDoc}
     */
    public RMNodeEvent getLastEvent() {
        return this.lastEvent;
    }

    /**
     * {@inheritDoc}
     */
    public void setLastEvent(final RMNodeEvent lastEvent) {
        this.lastEvent = lastEvent;
    }

    /**
     * {@inheritDoc}
     */
    public void setAddEvent(final RMNodeEvent addEvent) {
        this.addEvent = addEvent;
    }

    /**
     * {@inheritDoc}
     */
    public long getStateChangeTime() {
        return stateChangeTime;
    }

    /**
     * {@inheritDoc}
     */
    public void setJMXUrl(JMXTransportProtocol protocol, String address) {
        jmxUrls[protocol.ordinal()] = address;
    }

    public String getJMXUrl(JMXTransportProtocol protocol) {
        return jmxUrls[protocol.ordinal()];
    }

    public boolean isProtectedByToken() {
        return protectedByToken;
    }

    @Override
    public RMNodeEvent createNodeEvent(RMEventType eventType, NodeState previousNodeState, String initiator) {
        RMNodeEvent rmNodeEvent = new RMNodeEvent(toNodeDescriptor(), eventType, previousNodeState, initiator);
        // The rm node always keeps track on its last event, this is needed for rm node events logic
        if (eventType != null) {
            switch (eventType) {
                case NODE_ADDED:
                    this.setAddEvent(rmNodeEvent);
                    break;
            }
            this.setLastEvent(rmNodeEvent);
        }
        return rmNodeEvent;
    }

    private RMNodeDescriptor toNodeDescriptor() {
        RMNodeDescriptor rmNodeDescriptor = new RMNodeDescriptor();
        rmNodeDescriptor.setNodeURL(this.getNodeURL());
        rmNodeDescriptor.setNodeSourceName(this.getNodeSourceName());
        rmNodeDescriptor.setVNodeName(this.getVNodeName());
        rmNodeDescriptor.setHostName(this.getHostName());
        rmNodeDescriptor.setState(this.getState());
        rmNodeDescriptor.setDefaultJMXUrl(getJMXUrl(JMXTransportProtocol.RMI));
        rmNodeDescriptor.setProactiveJMXUrl(getJMXUrl(JMXTransportProtocol.RO));
        rmNodeDescriptor.setDescriptorVMName(this.getDescriptorVMName());
        rmNodeDescriptor.setStateChangeTime(this.getStateChangeTime());
        rmNodeDescriptor.setProviderName(getProvider() == null ? null : getProvider().getName());
        rmNodeDescriptor.setOwnerName(getOwner() == null ? null : getOwner().getName());
        rmNodeDescriptor.setNodeInfo(getNodeInfo());

        return rmNodeDescriptor;
    }

    @Override
    public RMNodeEvent createNodeEvent() {
        return createNodeEvent(null, null, null);
    }

    public void setProtectedByToken(boolean protectedByToken) {
        this.protectedByToken = protectedByToken;
    }
}