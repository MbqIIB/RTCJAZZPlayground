package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsMoveVersionablesAcrossComponentRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsMoveVersionablesInWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsMoveVersionablesWithinComponentRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsVersionable;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.MoveFoldersInWorkspaceResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IFolder;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionablePath;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import java.io.PrintStream;
import java.util.List;
import org.eclipse.osgi.util.NLS;


public class MoveInRepositoryCmd
  extends AbstractSubcommand
{
  public MoveInRepositoryCmd() {}
  
  public void run()
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    ICommandLine cli = config.getSubcommandCommandLine();
    ITeamRepository repo = null;
    

    ParmsWorkspace ws = null;
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(MoveInRepositoryCmdOpts.OPT_WORKSPACE_NAME.getId(), null), config);
    if (wsSelector != null) {
      SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    } else {
      repo = RepoUtil.loginUrlArgAnc(config, client);
      ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
    }
    
    IScmCommandLineArgument sourceComponentSelector = ScmCommandLineArgument.create(cli.getOptionValue(MoveInRepositoryCmdOpts.OPT_SOURCE_COMPONENT_SELECTOR, null), config);
    SubcommandUtil.validateArgument(sourceComponentSelector, RepoUtil.ItemType.COMPONENT);
    

    String sourceComponent = sourceComponentSelector.getItemSelector();
    UUID sourceComponentUUID = RepoUtil.lookupUuid(sourceComponent);
    if (sourceComponentUUID == null) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.MoveInRepositoryCmd_ComponentNotFound, sourceComponentSelector.getItemSelector()));
    }
    
    UUID targetComponentUUID = null;
    IScmCommandLineArgument targetComponentSelector = ScmCommandLineArgument.create(cli.getOptionValue(MoveInRepositoryCmdOpts.OPT_COMPONENT_SELECTOR, null), config);
    if (targetComponentSelector != null) {
      SubcommandUtil.validateArgument(targetComponentSelector, RepoUtil.ItemType.COMPONENT);
      
      String targetComponent = targetComponentSelector.getItemSelector();
      targetComponentUUID = RepoUtil.lookupUuid(targetComponent);
      if (targetComponentUUID == null) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.MoveInRepositoryCmd_ComponentNotFound, targetComponentSelector.getItemSelector()));
      }
    } else {
      targetComponentUUID = sourceComponentUUID;
    }
    

    String givenPath = null;
    if (cli.hasOption(MoveInRepositoryCmdOpts.OPT_TARGET_KEY)) {
      givenPath = cli.getOption(MoveInRepositoryCmdOpts.OPT_TARGET_KEY);
      String[] path = StringUtil.splitEscapedPath(givenPath);
      givenPath = path.length == 0 ? Character.toString('/') : toPath(path, false);
    } else {
      givenPath = Character.toString('/');
    }
    
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
      .getServiceInterface(IScmRichClientRestService.class);
    
    ScmVersionablePath targetPath = null;
    try {
      targetPath = RepoUtil.getVersionable2(scmService, ws.getWorkspaceHandle(), targetComponentUUID.getUuidValue(), givenPath, config);
    }
    catch (TeamRepositoryException localTeamRepositoryException1) {}
    
    if ((targetPath == null) || (targetPath.getVersionable() == null)) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.MoveInRepositoryCmd_RemoteTargetPathNotFound, givenPath));
    }
    IVersionableHandle targetVersionable = targetPath.getVersionable();
    if (!IFolder.ITEM_TYPE.equals(targetVersionable.getItemType())) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.MoveInRepositoryCmd_InvalidItemType, givenPath));
    }
    

    givenPath = cli.getOption(MoveInRepositoryCmdOpts.OPT_SOURCE_KEY);
    String[] path = StringUtil.splitEscapedPath(givenPath);
    givenPath = path.length == 0 ? Character.toString('/') : toPath(path, false);
    

    ScmVersionablePath sourcePath = null;
    try {
      sourcePath = RepoUtil.getVersionable2(scmService, ws.getWorkspaceHandle(), sourceComponentUUID.getUuidValue(), givenPath, config);
    }
    catch (TeamRepositoryException localTeamRepositoryException2) {}
    
    if ((sourcePath == null) || (sourcePath.getVersionable() == null)) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.MoveInRepositoryCmd_RemoteSourcePathNotFound, givenPath));
    }
    
    IVersionableHandle sourceVersionable = sourcePath.getVersionable();
    String destinationComment = cli.getOption(MoveInRepositoryCmdOpts.OPT_DESTINATION_CS_COMMENT, null);
    String sourceComment = cli.getOption(MoveInRepositoryCmdOpts.OPT_SOURCE_CS_COMMENT, null);
    
    ParmsMoveVersionablesInWorkspace moveParms = new ParmsMoveVersionablesInWorkspace();
    if (sourceComponentUUID.equals(targetComponentUUID))
    {
      ParmsMoveVersionablesWithinComponentRequest parmsMoveWithinComponentRequest = new ParmsMoveVersionablesWithinComponentRequest();
      moveWithinComponentRequests = new ParmsMoveVersionablesWithinComponentRequest[] { parmsMoveWithinComponentRequest };
      workspace = ws;
      componentItemId = sourceComponentUUID.getUuidValue();
      versionablesToMove = new ParmsVersionable[] { new ParmsVersionable(sourceVersionable) };
      parentFolderItemIds = new String[] { targetVersionable.getItemId().getUuidValue() };
      changeSetComment = (destinationComment == null ? "" : destinationComment);
    }
    else {
      ParmsMoveVersionablesAcrossComponentRequest parmsMoveAcrossComponentRequest = new ParmsMoveVersionablesAcrossComponentRequest();
      moveAcrossComponentRequests = new ParmsMoveVersionablesAcrossComponentRequest[] { parmsMoveAcrossComponentRequest };
      workspace = ws;
      sourceComponentItemId = sourceComponentUUID.getUuidValue();
      versionablesToMove = new ParmsVersionable[] { new ParmsVersionable(sourceVersionable) };
      targetComponentItemId = targetComponentUUID.getUuidValue();
      parentFolderItemIds = new String[] { targetVersionable.getItemId().getUuidValue() };
      targetChangeSetComment = (destinationComment == null ? "" : destinationComment);
      sourceChangeSetComment = (sourceComment == null ? "" : sourceComment);
    }
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED))
    {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
      pendingChangesDilemmaHandler.pendingChangesInSubcomponentsInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
      pendingChangesDilemmaHandler.pendingChangesInSubcomponentsInstruction = "cancel";
    }
    
    MoveFoldersInWorkspaceResultDTO result = null;
    try {
      result = client.postMoveVersionablesInWorkspace(moveParms, null);
    } catch (TeamRepositoryException e) {
      PermissionDeniedException pde = (PermissionDeniedException)SubcommandUtil.findExceptionByType(PermissionDeniedException.class, e);
      if (pde != null) {
        throw StatusHelper.permissionFailure(pde, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      throw StatusHelper.wrap(NLS.bind(Messages.MoveInRepositoryCmd_MoveFailed, givenPath), e, new IndentingPrintStream(config.getContext().stderr()), null);
    }
    if (result.isCancelled()) {
      if (result.getOutOfSyncShares().size() > 0) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      

      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result
        .getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(
          Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), 
          CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
    }
    config.getContext().stdout().println(Messages.MoveInRepositoryCmd_SUCCESSFULLY_COMPLETED);
  }
  
  private String toPath(String[] path, boolean isFolder) { return StringUtil.createPathString(path) + (isFolder ? Character.valueOf('/') : ""); }
}
