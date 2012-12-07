package org.atemsource.dojo.build;

/*
 * Copyright 2001-2005 The Apache Software Foundation. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;


/**
 * Goal builds an optimized dojo version from a build profile. All paths are relative to workDiSr
 * 
 * @goal build
 * @phase process-sources
 */
public class DojoBuildMojo extends AbstractMojo
{

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private String tempDir;

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private String releaseDir;

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private File workDir;

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private File debugFrom;

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private File debugTo;

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private String options;

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private String mainClass = "org.mozilla.javascript.tools.shell.Main";

	/**
	 * Location of the xml file.
	 * 
	 * @parameter
	 */
	private String profile;

	String changeName(String fileName, boolean debug)
	{
		if (!fileName.endsWith(".js"))
		{
			return fileName;
		}
		else if (fileName.endsWith(".uncompressed.js"))
		{
			if (debug)
			{
				return fileName.substring(0, fileName.length() - (".uncompressed.js".length()));
			}
			else
			{
				return null;
			}
		}
		else
		{
			if (debug)
			{
				return null;
			}
			else
			{
				return fileName;
			}
		}
	}

	void copyFiles(File fromDir, File toDir, boolean debug) throws IOException
	{
		for (File from : fromDir.listFiles())
		{
			if (from.getName().startsWith("."))
			{
				continue;
			}
			if (from.isDirectory())
			{
				File toFile = new File(toDir, from.getName());
				if (!toFile.exists())
				{
					toFile.mkdir();
				}
				copyFiles(from, toFile, debug);
			}
			else
			{
				String newFileName = changeName(from.getName(), debug);
				if (newFileName != null)
				{
					File toFile = new File(toDir, newFileName);
					FileUtils.copyFile(from, toFile);
					getLog().debug("copied: " + toFile.getAbsolutePath());
				}
			}
		}
	}

	public void execute() throws MojoExecutionException
	{

		boolean debug = System.getProperty("debugDojo") != null;
		String skipDojo = System.getProperty("skipDojo");
		if (skipDojo == null)
		{
			try
			{
				getLog().debug("workDir: " + workDir);
				getLog().debug("tempDir: " + tempDir);
				getLog().debug("tempDir: " + tempDir);
				List<String> params = new ArrayList<String>();
				String script;
				boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("win") >= 0;
				if (isWindows)
				{
					script = "build.bat";
				}
				else
				{

					script = "build.sh";
				}

				params.add(workDir.getAbsolutePath() + "/" + script);
				params.add("action=release");
				params.add("profile=" + profile);
				params.add("releaseDir=" + tempDir);
				if (options != null && !options.isEmpty())
				{
					for (String p : options.split(" "))
					{
						params.add(p);
					}
				}

					if (!isWindows)
					{
						chmod();
					}
				execute(params);
				// start.waitFor();
			}
			catch (MojoExecutionException e)
			{
				throw e;
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("cannot build dojo", e);
			}

			try
			{
				getLog().debug("- workDir: " + workDir);
				getLog().debug("- tempDir: " + tempDir);
				File fromDir = new File(workDir, tempDir);
				copyFiles(fromDir, new File(workDir, releaseDir), debug);
			}
			catch (Exception e)
			{
				getLog().error(e.getMessage());
				throw new MojoExecutionException("cannot process dojo", e);
			}
		}
		else
		{
			try
			{
				copyFiles(debugFrom, debugTo, false);
			}
			catch (IOException e)
			{
				getLog().error(e.getMessage());
			}
		}

	}

	private void execute(List<String> params) throws IOException, MojoExecutionException, InterruptedException
	{
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.directory(workDir);
		processBuilder.command(params);
		Process process = processBuilder.start();
		InputStream in = process.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		InputStream ein = process.getErrorStream();
		BufferedReader ereader = new BufferedReader(new InputStreamReader(ein));
		boolean finished = false;
		do
		{
			if (reader.ready())
			{
				String line = reader.readLine();
				if (line != null)
				{
					System.out.println(line);
				}

			}
			else if (ereader.ready())
			{
				String line = ereader.readLine();
				if (line != null)
				{
					System.err.println(line);
				}

			}
			else
			{
				try
				{
					int exit = process.exitValue();
					if (exit != 0)
					{
						throw new MojoExecutionException("dojo build ended with exit code " + exit);
					}
					else
					{
						finished = true;
					}
				}
				catch (IllegalThreadStateException e)
				{

				}
				Thread.sleep(100);
			}
		}
		while (!finished);
	}

	private void chmod() throws MojoExecutionException, IOException, InterruptedException
	{
		List<String> params = new ArrayList();
		params.add("chmod");
		params.add("u+x");
		params.add(workDir.getAbsolutePath() + "/build.sh");
		getLog().info("Changing rights on buildscript");
		execute(params);
	}
}
