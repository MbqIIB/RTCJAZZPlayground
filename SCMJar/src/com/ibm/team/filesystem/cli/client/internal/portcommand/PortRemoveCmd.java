package com.ibm.team.filesystem.cli.client.internal.portcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsAbortCurrentPatch;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsRemoveFromAcceptQueue;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.AbortCurrentPatchResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;











public class PortRemoveCmd
  extends AbstractPortSubcommand
{
  public static final NamedOptionDefinition OPT_REMOVE_CURRENT = new NamedOptionDefinition("c", "current", 0);
  public static final NamedOptionDefinition OPT_REMOVE_ALL = new NamedOptionDefinition("a", "all", 0);
  public static final NamedOptionDefinition OPT_REMOVE_SELECTOR = new NamedOptionDefinition("q", "queue", -1);
  
  public PortRemoveCmd() {}
  
  public void run() throws FileSystemException { ICommandLine cli = config.getSubcommandCommandLine();
    validateCommonArguments(cli);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument workspaceSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    IScmCommandLineArgument componentSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT), config);
    
    ParmsWorkspace targetWs = new ParmsWorkspace();
    ITeamRepository repo = null;
    
    if ((!cli.hasOption(OPT_REMOVE_CURRENT)) && (!cli.hasOption(OPT_REMOVE_ALL)) && (!cli.hasOption(OPT_REMOVE_SELECTOR))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.PortRemoveCmd_MissingOption, new String[] {
        OPT_REMOVE_CURRENT.getName(), OPT_REMOVE_ALL.getName(), OPT_REMOVE_SELECTOR.getName() }));
    }
    
    int actionCount = 0;
    if (cli.hasOption(OPT_REMOVE_CURRENT)) {
      actionCount++;
    }
    if (cli.hasOption(OPT_REMOVE_ALL)) {
      actionCount++;
    }
    if (cli.hasOption(OPT_REMOVE_SELECTOR)) {
      actionCount++;
    }
    if (actionCount > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_3_ARGUMENTS, new String[] {
        OPT_REMOVE_CURRENT.getName(), OPT_REMOVE_ALL.getName(), OPT_REMOVE_SELECTOR.getName() }));
    }
    

    if (workspaceSelector == null) {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
      if (wsInSandboxList.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.PortCmdOption_AmbiguousWorkspace, 
          cli.getDefinition().getOption(OPT_WORKSPACE).getName()));
      }
      
      ISandboxWorkspace wsInSandbox = (ISandboxWorkspace)wsInSandboxList.iterator().next();
      repositoryUrl = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
        Collections.singletonList(wsInSandbox));
      workspaceItemId = wsInSandbox.getWorkspaceItemId();
      
      repo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    } else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, workspaceSelector);
      
      IWorkspace ws = RepoUtil.getWorkspace(workspaceSelector.getItemSelector(), true, false, repo, config);
      
      repositoryUrl = repo.getRepositoryURI();
      workspaceItemId = ws.getItemId().getUuidValue();
    }
    
    WorkspaceComponentDTO componentDto = RepoUtil.getComponent(targetWs, componentSelector.getItemSelector(), client, config);
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    if ((cli.hasOption(OPT_REMOVE_ALL)) || (cli.hasOption(OPT_REMOVE_CURRENT)))
    {
      ParmsAbortCurrentPatch parmsAbort = new ParmsAbortCurrentPatch();
      workspace = targetWs;
      componentItemIds = new String[] { componentDto.getItemId() };
      action = (cli.hasOption(OPT_REMOVE_ALL) ? "discard_all" : "discard_current");
      
      preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
      pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
      if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
      } else {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
      }
      
      AbortCurrentPatchResultDTO result = null;
      try {
        result = client.postAbortCurrentPatch(parmsAbort, null);
      } catch (TeamRepositoryException tre) {
        throw StatusHelper.failure(Messages.PortRemoveCmd_FAILURE, tre);
      }
      
      if (result.isCancelled())
      {
        int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
        if (noOfUncheckedInChanges > 0) {
          throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, 
            Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
        }
      }
      
      out.println(cli.hasOption(OPT_REMOVE_ALL) ? Messages.PortRemoveCmd_All_SUCCESS : Messages.PortRemoveCmd_Current_SUCCESS);
      
      if (hasVerboseOption(cli)) {
        printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
      }
    }
    else if (cli.hasOption(OPT_REMOVE_SELECTOR)) {
      List<IScmCommandLineArgument> argSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_REMOVE_SELECTOR), config);
      SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.CHANGESET);
      
      if (argSelectors.size() <= 0) {
        throw StatusHelper.argSyntax(Messages.PortCmd_UnexpectedArguments);
      }
      List<String> ports = RepoUtil.getSelectors(argSelectors);
      
      String[] toRemove = new String[ports.size()];
      
      for (int i = 0; i < ports.size(); i++) {
        UUID uuid = RepoUtil.lookupUuid((String)ports.get(i));
        toRemove[i] = (uuid != null ? uuid.getUuidValue() : (String)ports.get(i));
      }
      
      ParmsRemoveFromAcceptQueue parmsRemove = new ParmsRemoveFromAcceptQueue();
      workspace = targetWs;
      componentItemId = componentDto.getItemId();
      ids = toRemove;
      try
      {
        client.postRemoveFromAcceptQueue(parmsRemove, null);
        out.println(Messages.PortRemoveCmd_Selector_SUCCESS);
        
        if (hasVerboseOption(cli)) {
          printPorts(repo, targetWs, componentDto.getItemId(), client, out, config);
        }
      } catch (TeamRepositoryException tre) {
        throw StatusHelper.wrap(Messages.PortRemoveCmd_FAILURE, tre, 
          out);
      }
    }
  }
  

  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    addVerboseToOptions(options);
    
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.PortCmdOption_WORKSPACE)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_REMOVE_ALL, Messages.PortRemoveCmdOption_All, true)
      .addOption(OPT_REMOVE_CURRENT, Messages.PortRemoveCmdOption_Current, true)
      .addOption(OPT_REMOVE_SELECTOR, Messages.PortRemoveCmdOption_Selector, true))
      .addOption(CommonOptions.OPT_POSITIONAL_ARG_SEPARATOR, NLS.bind(Messages.PortRemoveCmdOption_SEPARATOR, OPT_REMOVE_SELECTOR.getName()))
      .addOption(OPT_COMPONENT, Messages.PortCmdOption_COMPONENT);
    
    return options;
  }
  
  protected void validateCommonArguments(ICommandLine cli) throws FileSystemException
  {
    IScmCommandLineArgument workspace = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    SubcommandUtil.validateArgument(workspace, RepoUtil.ItemType.WORKSPACE);
    
    IScmCommandLineArgument component = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT.getId()), config);
    SubcommandUtil.validateArgument(component, RepoUtil.ItemType.COMPONENT);
  }
}
