package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPostValidateSandbox;
import com.ibm.team.filesystem.common.internal.rest.client.corruption.MetadataValidationResultDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import org.eclipse.osgi.util.NLS;



public class ValidateMetadataCmd
  extends AbstractSubcommand
{
  public ValidateMetadataCmd() {}
  
  public void run()
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    File sandboxFile = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
    if (sandboxFile == null) {
      throw StatusHelper.misconfiguredLocalFS(NLS.bind(Messages.RepairCmd_NO_SANDBOX_METADATA, config.getContext().getCurrentWorkingDirectory()));
    }
    
    ICommandLine cli = config.getSubcommandCommandLine();
    
    String logfilePath;
    
    if (cli.hasOption(ValidateMetadataCmdOpts.OPT_LOGFILE)) {
      IScmCommandLineArgument logfileSelector = ScmCommandLineArgument.create(cli.getOptionValue(ValidateMetadataCmdOpts.OPT_LOGFILE), config);
      logfilePath = logfileSelector.getStringValue();
    }
    else {
      try {
        File logFile = File.createTempFile("validate_", ".log", new File(config.getContext().getCurrentWorkingDirectory()));
        logfilePath = logFile.getAbsolutePath();
      } catch (IOException e) { String logfilePath;
        throw StatusHelper.failure(NLS.bind(Messages.ValidateMetadataCmd_5, config.getContext().getCurrentWorkingDirectory()), e);
      }
    }
    String logfilePath;
    ParmsPostValidateSandbox validateParms = new ParmsPostValidateSandbox();
    sandboxPath = sandboxFile.getAbsolutePath();
    logFilePath = logfilePath;
    getDump = Boolean.valueOf(cli.hasOption(ValidateMetadataCmdOpts.OPT_DUMP));
    
    MetadataValidationResultDTO result = null;
    try {
      config.getContext().stdout().println(NLS.bind(Messages.ValidateMetadataCmd_2, sandboxFile));
      result = client.postValidateSandbox(validateParms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.misconfiguredRemoteFS(e.getMessage());
    }
    
    if (result.isIsValid()) {
      if (getDump.booleanValue()) {
        config.getContext().stdout().println(NLS.bind(Messages.ValidateMetadataCmd_7, sandboxPath, logfilePath));
      } else {
        new File(logfilePath).delete();
        config.getContext().stdout().println(NLS.bind(Messages.ValidateMetadataCmd_6, sandboxPath));
      }
    } else {
      throw StatusHelper.failure(NLS.bind(Messages.ValidateMetadataCmd_8, sandboxPath, logfilePath), null);
    }
  }
}
