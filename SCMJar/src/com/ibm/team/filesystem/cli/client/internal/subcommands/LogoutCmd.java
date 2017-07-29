package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.CommandLineCore;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLogout;
import com.ibm.team.filesystem.client.util.IRepositoryRecord;
import com.ibm.team.filesystem.client.util.IRepositoryRegistry;
import com.ibm.team.process.internal.common.NLS;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.io.PrintStream;






public class LogoutCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public LogoutCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.LogoutCmd_0);
    
    options.addOption(CommonOptions.OPT_URI, Messages.LogoutCmd_1, true);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    String repoURI = subargs.getOption(CommonOptions.OPT_URI, null);
    
    if (repoURI == null) {
      throw StatusHelper.argSyntax(Messages.LogoutCmd_2);
    }
    

    IRepositoryRecord rec = config.getRepositoryRegistry().getRecord(repoURI);
    if (rec != null) {
      repoURI = rec.getUrl();
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    ParmsLogout parms = new ParmsLogout();
    repositoryUrl = repoURI;
    try
    {
      client.postLogout(parms, null);
    } catch (IllegalArgumentException localIllegalArgumentException) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.LogoutCmd_5, repoURI));
    } catch (TeamRepositoryException localTeamRepositoryException) {
      if (rec == null) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.LogoutCmd_3, repoURI));
      }
    }
    

    if (rec != null) {
      config.getRepositoryRegistry().removeRecord(rec);
      config.getContext().stdout().println(Messages.LogoutCmd_4 + repoURI);
    } else {
      throw StatusHelper.createException(NLS.bind(Messages.LogoutCmd_CONCATENATE, NLS.bind(Messages.LogoutCmd_6, repoURI), NLS.bind(Messages.LogoutCmd_7, CommandLineCore.getConfig().getContext().getAppName())), 1, null);
    }
    
    config.removeConnectionInfo(repoURI);
  }
}
