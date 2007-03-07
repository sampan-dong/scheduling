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
package org.objectweb.proactive.core.process;

import java.io.IOException;


/**
 * This class contains a list of processes that have a dependency
 * with its predecessor.
 * @author ProActiveTeam
 * @version 1.0, 01 Dec 2005
 * @since ProActive 3.0
 *
 */
public class DependentListProcess extends AbstractSequentialListProcessDecorator {
    public DependentListProcess() {
        super();
    }

    /**
     * Add a process to the processes queue - first process is not a dependent process unlike
     * the others
     * @param process
     */
    public void addProcessToList(ExternalProcess process) {
        if (processes.size() == 0) {
            processes.add(process);
        }
        // process has to be an instance of dependentProcess
        // implements DependentProcess
        else if (process instanceof DependentProcess) {
            this.processes.add(process);
        } else {
            throw new ClassCastException(
                " process must be a dependent process !");
        }
    }

    /**
     * @see org.objectweb.proactive.core.process.UniversalProcess#getProcessId()
     */
    public String getProcessId() {
        return "dps";
    }

    public boolean isSequential() {
        return true;
    }

    public boolean isDependent() {
        return true;
    }

    public boolean isHierarchical() {
        return false;
    }

    public String getHostname() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setHostname(String hostname) {
        // TODO Auto-generated method stub
    }

    protected ExternalProcess createProcess() {
        // TODO Auto-generated method stub
        return null;
    }

    public void startProcess() throws IOException {
        // TODO Auto-generated method stub
    }
}
