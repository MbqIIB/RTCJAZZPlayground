package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ISandbox;
import com.ibm.team.filesystem.client.workitems.IFileSystemWorkItemRestClient;
import com.ibm.team.filesystem.client.workitems.rest.parameters.ParmsPostChangeSetWorkItem;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.workitem.common.model.IWorkItem;
import java.io.PrintStream;

public class ChangesetAssociateWorkitemCmd
  extends ChangesetWorkitemBase
{
  public ChangesetAssociateWorkitemCmd() {}
  
  protected void doIt()
    throws FileSystemException
  {
    setWorkitem(wsId, csDTO.getChangeSetItemId(), workItem.getItemId().getUuidValue(), repoUri, 
      wiRepoUri, config);
    config.getContext().stdout().println(Messages.ChangesetAssociateWorkitemCmd_8);
  }
  
  public static void setWorkitem(String wsId, String csId, String wiId, String repoUri, String wiRepoUri, IScmClientConfiguration config) throws FileSystemException
  {
    ISandbox[] sandboxes = config.getSandboxes();
    IFileSystemWorkItemRestClient client = (IFileSystemWorkItemRestClient)SubcommandUtil.startDaemon(
      IFileSystemWorkItemRestClient.class, sandboxes, config);
    
    ParmsPostChangeSetWorkItem parmsCsWi = new ParmsPostChangeSetWorkItem();
    repositoryUrl = repoUri;
    workspaceId = wsId;
    changeSetId = csId;
    workItemItemId = wiId;
    if (wiRepoUri != null) {
      wiRepositoryUrl = wiRepoUri;
    }
    try
    {
      client.postAddWorkItem(parmsCsWi, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ChangesetAssociateWorkitemCmd_7, e, 
        new IndentingPrintStream(config.getContext().stderr()), repoUri);
    }
  }
}
