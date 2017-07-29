package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ISandbox;
import com.ibm.team.filesystem.client.internal.calm.OslcLinkUtil;
import com.ibm.team.filesystem.client.internal.rest.CommonUtil;
import com.ibm.team.filesystem.client.workitems.IFileSystemWorkItemRestClient;
import com.ibm.team.filesystem.client.workitems.rest.parameters.ParmsPostChangeSetWorkItem;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkItemSyncDTO;
import com.ibm.team.links.common.ILink;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.workitem.common.model.IWorkItem;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import org.eclipse.osgi.util.NLS;

public class ChangesetDisassociateWorkitemCmd
  extends ChangesetWorkitemBase
{
  public ChangesetDisassociateWorkitemCmd() {}
  
  protected void doIt()
    throws FileSystemException
  {
    boolean isWiLinkedToCs = false;
    for (WorkItemSyncDTO wiSync : csDTO.getWorkItems()) {
      if (wiSync.getWorkItemItemId().equals(workItem.getItemId().getUuidValue())) {
        isWiLinkedToCs = true;
      }
    }
    
    if (!isWiLinkedToCs)
    {
      boolean isOslcLinked = false;
      try { isOslcLinked = isOslcLinked();
      } catch (TeamRepositoryException localTeamRepositoryException1) {}
      if (!isOslcLinked) {
        ICommandLine cli = config.getSubcommandCommandLine();
        IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_CHANGESET.getId()), config);
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ChangesetDisassociateWorkitemCmd_4, csSelector.getItemSelector(), Integer.valueOf(workItem.getId())));
      }
    }
    

    ISandbox[] sandboxes = config.getSandboxes();
    IFileSystemWorkItemRestClient client = (IFileSystemWorkItemRestClient)SubcommandUtil.startDaemon(IFileSystemWorkItemRestClient.class, sandboxes, config);
    

    ParmsPostChangeSetWorkItem parmsCsWi = new ParmsPostChangeSetWorkItem();
    repositoryUrl = repoUri;
    workspaceId = wsId;
    changeSetId = csDTO.getChangeSetItemId();
    workItemItemId = workItem.getItemId().getUuidValue();
    if (wiRepoUri != null) {
      wiRepositoryUrl = wiRepoUri;
    }
    try
    {
      client.postRemoveWorkItem(parmsCsWi, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ChangesetDisassociateWorkitemCmd_1, e, new IndentingPrintStream(config.getContext().stderr()), repoUri);
    }
    
    config.getContext().stdout().println(Messages.ChangesetDisassociateWorkitemCmd_3);
  }
  
  private boolean isOslcLinked() throws TeamRepositoryException {
    boolean oslcLink = false;
    ITeamRepository repo = CommonUtil.getTeamRepository(repoUri);
    if (!repo.loggedIn()) {
      repo.login(null);
    }
    IChangeSetHandle changeSetHandle = (IChangeSetHandle)IChangeSet.ITEM_TYPE.createItemHandle(repo, UUID.valueOf(csDTO.getChangeSetItemId()), null);
    List<ILink> links = OslcLinkUtil.fetchScmOslcLinks(repo, Collections.singleton(changeSetHandle), null);
    for (ILink link : links) {
      if (OslcLinkUtil.isScmOslcLink(link)) {
        oslcLink = true;
        break;
      }
    }
    
    return oslcLink;
  }
}
