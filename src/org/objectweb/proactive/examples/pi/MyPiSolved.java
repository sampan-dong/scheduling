/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2007 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@objectweb.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package org.objectweb.proactive.examples.pi;

import org.objectweb.proactive.ProActive;
import org.objectweb.proactive.core.descriptor.data.ProActiveDescriptor;
import org.objectweb.proactive.core.descriptor.data.VirtualNode;
import org.objectweb.proactive.core.group.ProActiveGroup;
import org.objectweb.proactive.core.node.Node;


public class MyPiSolved {
    public static void main(String[] args) throws Exception {
        Integer numberOfDecimals = new Integer(args[0]);
        String descriptorPath = args[1];

        ProActiveDescriptor descriptor = ProActive.getProactiveDescriptor(descriptorPath);
        descriptor.activateMappings();
        VirtualNode virtualNode = descriptor.getVirtualNode("computers-vn");
        Node[] nodes = virtualNode.getNodes();

        PiComputer piComputer = (PiComputer) ProActiveGroup.newGroupInParallel(PiComputer.class.getName(),
                new Object[] { numberOfDecimals }, nodes);

        int numberOfWorkers = ProActiveGroup.getGroup(piComputer).size();

        Interval intervals = PiUtil.dividePI(numberOfWorkers,
                numberOfDecimals.intValue());
        ProActiveGroup.setScatterGroup(intervals);

        Result results = piComputer.compute(intervals);
        Result result = PiUtil.conquerPI(results);
        System.out.println("Pi:" + result);

        descriptor.killall(true);
        System.exit(0);
    }
}
