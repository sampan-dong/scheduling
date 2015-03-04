/*
 *  *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2014 INRIA/University of
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
 *  * Tobias Wiens
 */
package org.ow2.proactive.scheduler.newimpl;

import org.apache.log4j.Logger;
import org.objectweb.proactive.extensions.processbuilder.OSProcessBuilder;
import org.objectweb.proactive.extensions.processbuilder.exception.CoreBindingException;
import org.objectweb.proactive.extensions.processbuilder.exception.FatalProcessBuilderException;
import org.objectweb.proactive.extensions.processbuilder.exception.OSUserException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;


/**
 * Helper class to run commands and deal with failures.
 */
public class PBCommandExecutor extends TimedCommandExecutor {

    private static final Logger logger = Logger.getLogger(PBCommandExecutor.class);

    // Members
    OSProcessBuilder processBuilder;

    /**
     * Constructor.
     *
     * @param processBuilder Process builder which will be used to spawn new process in which
     *                       commands will be executed.
     */
    public PBCommandExecutor(OSProcessBuilder processBuilder) {
        this.processBuilder = processBuilder;

    }

    /**
     * Executes a command and handles interrupts. When either outputSink or errorSink is null, neither of
     * the streams will be served with input.
     *
     * @param outputSink Standard output, which will be linked to the process with a ProcessStreamReader.
     * @param errorSink  Error output, which will be linked to the process with a ProcessStreamReader.
     * @param command    Command which will be executed.
     * @return Exit code of command
     * @throws InterruptedException         Thrown when thread is interrupted during command execution.
     */
    @Override
    public int executeCommand(PrintStream outputSink, PrintStream errorSink, String... command)
            throws InterruptedException, FailedExecutionException {
        Process process;
        ProcessStreamsReader processStreamsReader = null;

        // Set command and start process
        this.processBuilder.command(command);

        try {
            // Start process
            process = this.processBuilder.start();

            // Attach stream readers
            if (outputSink != null && errorSink != null) {
                processStreamsReader = new ProcessStreamsReader(process, outputSink, errorSink);
            }

            return process.waitFor();

        } catch (InterruptedException e) {
            PBCommandExecutor.logger.info("Command " + Arrays.toString(command) +
                " was interrupted. Cleaning up.");
            throw e;
        } catch (OSUserException e) {
            PBCommandExecutor.logger.warn("Unable to execute command:\n" + Arrays.toString(command), e);
            throw new FailedExecutionException(e.getMessage());
        } catch (CoreBindingException e) {
            PBCommandExecutor.logger.warn("Unable to execute command:\n" + Arrays.toString(command), e);
            throw new FailedExecutionException(e.getMessage());
        } catch (FatalProcessBuilderException e) {
            PBCommandExecutor.logger.warn("Unable to execute command:\n" + Arrays.toString(command), e);
            throw new FailedExecutionException(e.getMessage());
        } catch (IOException e) {
            PBCommandExecutor.logger.warn("Unable to execute command:\n" + Arrays.toString(command), e);
            throw new FailedExecutionException(e.getMessage());
        } finally {
            // Waits for content to be flushed
            if (processStreamsReader != null) {
                processStreamsReader.close();
            }

        }
    }

}