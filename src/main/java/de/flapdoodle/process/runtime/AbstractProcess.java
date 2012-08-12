/**
 * Copyright (C) 2011
 *   Michael Mosmann <michael@mosmann.de>
 *   Martin Jöhren <m.joehren@googlemail.com>
 *
 * with contributions from
 * 	konstantin-ba@github,Archimedes Trajano (trajano@github)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.flapdoodle.process.runtime;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import de.flapdoodle.process.config.ExecutableProcessConfig;
import de.flapdoodle.process.config.IRuntimeConfig;
import de.flapdoodle.process.config.ISupportConfig;
import de.flapdoodle.process.config.io.ProcessOutput;
import de.flapdoodle.process.distribution.Distribution;
import de.flapdoodle.process.io.Processors;
import de.flapdoodle.process.io.StreamToLineProcessor;

public abstract class AbstractProcess<T extends ExecutableProcessConfig, E extends Executable<T, P>, P> {

	private static Logger logger = Logger.getLogger(AbstractProcess.class.getName());

	public static final int TIMEOUT = 20000;

	private final T config;
	private final IRuntimeConfig runtimeConfig;
	private final E executable;
	private ProcessControl process;
	private int processId;

	private boolean stopped = false;

	private Distribution distribution;

	public AbstractProcess(Distribution distribution, T config, IRuntimeConfig runtimeConfig, E mongodExecutable)
			throws IOException {
		this.config = config;
		this.runtimeConfig = runtimeConfig;
		this.executable = mongodExecutable;
		this.distribution = distribution;

		ProcessOutput outputConfig = runtimeConfig.getProcessOutput();

		try {

			onBeforeProcess(runtimeConfig);

			process = ProcessControl.fromCommandLine(
					supportConfig(),
					runtimeConfig.getCommandLinePostProcessor().process(distribution,
							getCommandLine(distribution, config, this.executable.getFile())), true);

			Runtime.getRuntime().addShutdownHook(new JobKiller());

			onAfterProcess(process, runtimeConfig);

		} catch (IOException iox) {
			stop();
			throw iox;
		}
	}
	
	public T getConfig() {
		return config;
	}

	protected void onAfterProcess(ProcessControl process, IRuntimeConfig runtimeConfig) throws IOException {
		ProcessOutput outputConfig = runtimeConfig.getProcessOutput();
		Processors.connect(process.getReader(), outputConfig.getOutput());
		Processors.connect(process.getError(), StreamToLineProcessor.wrap(outputConfig.getError()));
	}

	protected void onBeforeProcess(IRuntimeConfig runtimeConfig2) throws IOException {

	}

	protected abstract List<String> getCommandLine(Distribution distribution, T config, File exe) throws IOException;

	protected abstract ISupportConfig supportConfig();

	public abstract void stop();

	protected void stopProcess() {
		process.stop();
	}

	protected void setProcessId(int processId) {
		this.processId = processId;
	}

	protected boolean sendKillToProcess() {
		if (processId > 0) {
			return ProcessControl.killProcess(supportConfig(), distribution.getPlatform(),
					StreamToLineProcessor.wrap(runtimeConfig.getProcessOutput().getCommands()), processId);
		}
		return false;
	}

	protected boolean tryKillToProcess() {
		if (processId > 0) {
			return ProcessControl.tryKillProcess(supportConfig(), distribution.getPlatform(),
					StreamToLineProcessor.wrap(runtimeConfig.getProcessOutput().getCommands()), processId);
		}
		return false;
	}

	/**
	 *
	 */
	class JobKiller extends Thread {

		@Override
		public void run() {
			AbstractProcess.this.stop();
		}
	}

}