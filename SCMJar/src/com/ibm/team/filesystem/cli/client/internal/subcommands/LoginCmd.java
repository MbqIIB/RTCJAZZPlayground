package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.ConnectionInfo;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.utils.RepositoryRegistry.RepositoryRecord;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.util.IRepositoryRegistry;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.io.PrintStream;
import org.eclipse.osgi.util.NLS;














public class LoginCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public LoginCmd() {}
  
  public static final IOptionKey OPT_NICKNAME = new OptionKey("nick");
  
  public static final IOptionKey OPT_CACHE = new OptionKey("cache");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.LoginCmd_0);
    
    SubcommandUtil.addRepoLocationToOptions(options, true, true);
    
    options.addOption(OPT_NICKNAME, "n", "nickname", Messages.LoginCmd_1, 1);
    options.addOption(OPT_CACHE, "c", "cache", Messages.LoginCmdOptions_CACHE_HELP, 0);
    
    return options;
  }
  
  public void run()
    throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    String url = subargs.getOption(CommonOptions.OPT_URI, null);
    
    if (url == null) {
      throw StatusHelper.argSyntax(Messages.LoginCmd_2);
    }
    
    ConnectionInfo info = config.getConnectionInfo(url, null, true, false);
    

    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.login(config, client, info);
    

    String passwordToStore = null;
    if ((subargs.hasOption(CommonOptions.OPT_PASSWORD)) || ((subargs.hasOption(OPT_CACHE)) && (info.getPassword() != null))) {
      passwordToStore = info.getPassword();
    }
    

    String nick = subargs.getOption(OPT_NICKNAME, null);
    

    UUID userid = info.getUserUUID();
    

    IRepositoryRegistry repoReg = config.getRepositoryRegistry();
    RepositoryRegistry.RepositoryRecord record = new RepositoryRegistry.RepositoryRecord(info.getURI(), nick, info.getUsername(), passwordToStore, 
      info.getCertificateFile() != null ? info.getCertificateFile().toOSString() : null, info.isSmartCard(), info.isKerberos(), info.isIntegratedWindows(), repo.getId(), userid);
    
    repoReg.addRecord(record);
    
    config.getContext().stdout().println(NLS.bind(Messages.LoginCmd_7, url));
  }
}
