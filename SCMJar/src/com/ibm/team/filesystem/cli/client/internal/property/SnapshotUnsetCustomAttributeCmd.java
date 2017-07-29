package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSetCustomAttributes;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;










public class SnapshotUnsetCustomAttributeCmd
{
  IFilesystemRestClient client;
  
  public SnapshotUnsetCustomAttributeCmd() {}
  
  public static void unsetCustomAttributes(IScmCommandLineArgument ssSelector, IScmCommandLineArgument wsSelector, String[] unsetCustomAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    

    IWorkspace ws = null;
    if (wsSelector != null) {
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    }
    

    IBaselineSet ss = RepoUtil.getSnapshot(ws != null ? ws.getItemId().getUuidValue() : null, ssSelector.getItemSelector(), repo, config);
    

    unsetCustomAttributes(ss, unsetCustomAttrs, repo, client, config);
    
    config.getContext().stdout().println(Messages.SnapshotUnsetCustomAttributesCmd_PROPERTY_UNSET_SUCCESS);
  }
  
  private static void unsetCustomAttributes(IBaselineSet ss, String[] unsetCustomAttrs, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsBaselineSetCustomAttributes parms = new ParmsBaselineSetCustomAttributes();
    baselineSet = new ParmsBaselineSet();
    baselineSet.baselineSetItemId = ss.getItemId().getUuidValue();
    baselineSet.repositoryUrl = repo.getRepositoryURI();
    
    unsetCustomAttrs = unsetCustomAttrs;
    
    try
    {
      client.postSetBaselineSetCustomAttributes(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.SnapshotUnsetCustomAttributesCmd_PROPERTY_UNSET_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
}
