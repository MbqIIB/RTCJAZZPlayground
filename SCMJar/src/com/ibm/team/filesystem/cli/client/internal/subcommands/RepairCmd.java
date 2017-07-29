package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsRebuildSandbox;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.corruption.RebuildCopyFileAreaDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.osgi.util.NLS;


public class RepairCmd
  extends AbstractSubcommand
{
  public RepairCmd() {}
  
  public void run()
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    File sandboxPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
    if (sandboxPath == null) {
      throw StatusHelper.misconfiguredLocalFS(NLS.bind(Messages.RepairCmd_NO_SANDBOX_METADATA, config.getContext().getCurrentWorkingDirectory()));
    }
    

    ILocation sandboxLoc = new PathLocation(sandboxPath.getAbsolutePath());
    List<ShareDTO> shares = RepoUtil.getSharesInSandbox(null, sandboxLoc, client, config);
    if (shares.size() == 0) {
      throw StatusHelper.misconfiguredLocalFS(Messages.RepairCmd_4);
    }
    

    ICommandLine cli = config.getSubcommandCommandLine();
    ITeamRepository repo = null;
    if (cli.hasOption(CommonOptions.OPT_URI)) {
      String repoUriSelector = cli.getOption(CommonOptions.OPT_URI, null);
      repo = RepoUtil.login(config, client, config.getConnectionInfo(repoUriSelector));
    }
    

    List<String> repoIds = new ArrayList();
    for (ShareDTO share : shares) {
      if (!repoIds.contains(share.getRepositoryId())) {
        repoIds.add(share.getRepositoryId());
      }
    }
    

    boolean repoMatched = false;
    for (String repoId : repoIds) {
      if (repo == null) {
        try {
          String repoUri = RepoUtil.getRepoUri(config, client, repoId);
          RepoUtil.login(config, client, config.getConnectionInfo(repoUri));
        } catch (Exception e) {
          String errMsg = e.getLocalizedMessage() + System.getProperty("line.separator") + 
            NLS.bind(Messages.RepairCmd_REPO_SUGGESTION, CommonOptions.OPT_URI.getName());
          throw StatusHelper.login(errMsg, null);
        }
      } else if (repo.getId().getUuidValue().equals(repoId)) {
        repoMatched = true;
      }
    }
    

    if ((repo != null) && (!repoMatched)) {
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.RepairCmd_INVALID_REPOURI, 
        repo.getRepositoryURI()));
    }
    
    config.getContext().stdout().println(Messages.RepairCmd_0);
    
    ParmsRebuildSandbox parms = new ParmsRebuildSandbox();
    sandboxPath = sandboxPath.getAbsolutePath();
    
    RebuildCopyFileAreaDTO result = null;
    try {
      result = client.postRebuildCopyFileArea(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.failure(Messages.RepairCmd_6, e);
    }
    
    if (result.isNoDescriptorsFound()) {
      config.getContext().stderr().println(Messages.RepairCmd_4);
      throw StatusHelper.failure(Messages.RepairCmd_6, null);
    }
    
    if (result.isIgnoredErrors()) {
      config.getContext().stderr().println(Messages.RepairCmd_5);
    }
    
    config.getContext().stdout().println(Messages.RepairCmd_1);
  }
}
