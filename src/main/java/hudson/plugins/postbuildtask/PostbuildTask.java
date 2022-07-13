/*
 * The MIT License
 *
 * Copyright (c) 2009-2011, Ushus Technologies LTD., Shinod K Mohandas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.postbuildtask;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.Shell;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONTokener;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
 * Post build tasks added as {@link Recorder}.
 *
 * @author Shinod Mohandas
 */
public class PostbuildTask extends Recorder {

	private final TaskProperties[] tasks;

	public PostbuildTask(TaskProperties... tasks) {
		this.tasks = tasks;
	}

	public PostbuildTask(Collection<TaskProperties> tasks) {
		this(tasks.toArray(new TaskProperties[tasks.size()]));
	}

	/**
	 * This method will return the taskProperties foe the specified logText
	 *
	 * @return all the tasks.
	 */
	// TODO need to finish later
	public TaskProperties[] getAllTasks() {
		return Arrays.copyOf(tasks, tasks.length);
	}

	/**
	 * This method will return all the tasks
	 *
	 * @return all the tasks.
	 */
	public List<TaskProperties> getTasks() {
		if (tasks == null)
			return new ArrayList<TaskProperties>();
		else
			return Collections.unmodifiableList(Arrays.asList(tasks));
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		String buildLog = build.getLog();
		listener.getLogger().println("Performing Post build task...");

     	Result pr = build.getResult();

		try {
			for (int i = 0; i < tasks.length; i++) {
				TaskProperties taskProperties = tasks[i];
				String script = taskProperties.script;
				if (checkLogTextMatch(taskProperties.logText,
						buildLog, listener)) {
					listener.getLogger().println(
							"Logical operation result is TRUE");

					if (taskProperties.RunIfJobSuccessful && pr!=null && pr.isWorseThan(Result.UNSTABLE)) {
						listener.getLogger().println("Skipping post build task "+i+" - job status is worse than unstable : "+build.getResult());
						continue;
					}

					listener.getLogger().println("Running script  : " + script);
					CommandInterpreter runner = getCommandInterpreter(launcher,
							script);
					Result result = runner.perform(build, launcher, listener) ? Result.SUCCESS
							: Result.FAILURE;
					listener.getLogger().println(
							"POST BUILD TASK : " + result.toString());
					listener.getLogger().println(
							"END OF POST BUILD TASK : " + i);

					if((result == Result.FAILURE) && (taskProperties.EscalateStatus)) {
							listener.getLogger().println("ESCALATE FAILED POST BUILD TASK TO JOB STATUS");
							build.setResult(Result.FAILURE);
					}
				} else {
					listener.getLogger().println(
							"Logical operation result is FALSE");
					listener.getLogger()
							.println("Skipping script  : " + script);
					listener.getLogger().println(
							"END OF POST BUILD TASK 	: " + i);
				}
			}
		} catch (Exception e) {
			listener.getLogger().println(
					"Exception when executing the batch command : "
							+ e.getMessage());
			return false;
		}
		return true;
	}

	private boolean checkLogTextMatch(String logText,
			String buildLog, BuildListener listener) {
		return isMatching(buildLog, logText, listener);
	}

	private boolean isMatching(String buildLog, String logText,
			BuildListener listener) {
		Pattern pattern = Pattern.compile(logText);
		Matcher matcher = pattern.matcher(buildLog);
		boolean match = matcher.find();
		if (match) {
			listener.getLogger().println(
					"Match found for :" + logText + " : True");
		} else {
			listener.getLogger().println(
					"Could not match :" + logText + "  : False");
		}
		return match;
	}

	/**
	 * This method will return the command intercepter as per the node OS
	 *
	 * @param launcher
	 * @param script
	 * @return CommandInterpreter
	 */
	private CommandInterpreter getCommandInterpreter(Launcher launcher,
			String script) {
		if (launcher.isUnix())
			return new Shell(script);
		else
			return new BatchFile(script);
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		public DescriptorImpl() {
			super(PostbuildTask.class);
			load();
		}

		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Post build task";
		}

		@Override
		public String getHelpFile() {
			return "/plugin/postbuild-task/help/main.html";
		}

		@Override
		public PostbuildTask newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {

			if (req == null) {
				throw new IllegalStateException("req is always non null");
			}

			// if(req.getParameter("postbuild-task.")!=null)
			List<TaskProperties> tasksprops = new ArrayList<TaskProperties>();
			Object object = new JSONTokener(formData.getString("t")).nextValue();
			if (object instanceof JSONArray) {
				JSONArray arr = formData.getJSONArray("t");
				for (int i=0; i < arr.size(); i++) {
					JSONObject obj = arr.getJSONObject(i);
					tasksprops.add(new TaskProperties(
					    obj.getString("logText"),
							obj.getString("script"),
							obj.getBoolean("EscalateStatus"),
							obj.getBoolean("RunIfJobSuccessful")
					));
				}
			} else if (object instanceof JSONObject) {
				JSONObject obj = formData.getJSONObject("t");
				tasksprops.add(new TaskProperties(
						obj.getString("logText"),
						obj.getString("script"),
						obj.getBoolean("EscalateStatus"),
						obj.getBoolean("RunIfJobSuccessful")
				));
			}

			return new PostbuildTask(tasksprops);
		}
	}
}
