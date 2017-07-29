package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil.ItemInfo;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLineArgument;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;














public class ListChangesCmd
  extends AbstractSubcommand
{
  public ListChangesCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    if ((cli.hasOption(ListChangesCmdOptions.OPT_SNAPSHOT)) && (cli.hasOption(ListChangesCmdOptions.OPT_BASELINE))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, new String[] {
        ListChangesCmdOptions.OPT_SNAPSHOT.getName(), ListChangesCmdOptions.OPT_BASELINE.getName() }));
    }
    
    ITeamRepository repo = null;
    ParmsWorkspace ws = null;
    String itemID = null;
    String contextType = null;
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ListChangesCmdOptions.OPT_WORKSPACE, null), config);
    if (wsSelector != null) {
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
      
      itemID = workspaceItemId;
      contextType = "workspace";
    }
    
    IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(ListChangesCmdOptions.OPT_SNAPSHOT, null), config);
    if (ssSelector != null) {
      if (repo != null) {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.SNAPSHOT, Collections.singletonList(ssSelector), repo, config);
      }
      else {
        repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
      }
      SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
      IBaselineSet snapshot = RepoUtil.getSnapshot(ws != null ? workspaceItemId : null, 
        ssSelector.getItemSelector(), repo, config);
      itemID = snapshot.getItemId().getUuidValue();
      contextType = "baselineset";
    }
    
    IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(ListChangesCmdOptions.OPT_BASELINE, null), config);
    if (blSelector != null) {
      if (!cli.hasOption(ListChangesCmdOptions.OPT_WORKSPACE))
      {
        if (blSelector.getRepositorySelector() != null) {
          repo = RepoUtil.login(config, client, 
            config.getConnectionInfo(blSelector.getRepositorySelector()));
        }
        
        ws = RepoUtil.findWorkspaceInSandbox(null, repo != null ? repo.getId() : null, 
          client, config);
        if (repo == null) {
          repo = RepoUtil.login(config, client, 
            config.getConnectionInfo(repositoryUrl));
        }
      }
      
      RepoUtil.validateItemRepos(RepoUtil.ItemType.BASELINE, Collections.singletonList(blSelector), repo, config);
      SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
      itemID = getBaselineItemID(workspaceItemId, blSelector, client, config);
      contextType = "baseline";
    }
    
    List<IScmCommandLineArgument> csSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(ListChangesCmdOptions.OPT_CHANGESET), config);
    
    listChanges(csSelectors, itemID, contextType, repo, client, config, cli);
  }
  

  String getBaselineItemID(String wsId, IScmCommandLineArgument selector, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    String itemID = null;
    String blSelector = selector.getItemSelector();
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, selector);
    String blUUID = null;
    
    UUID uuid = RepoUtil.lookupUuid(blSelector);
    if (uuid != null) {
      blUUID = uuid.getUuidValue();
    }
    
    List<WorkspaceDetailsDTO> result = RepoUtil.getWorkspaceDetails(Collections.singletonList(
      new ParmsWorkspace(repo.getRepositoryURI(), wsId)), client, config);
    if (result.size() != 1) {
      throw StatusHelper.failure(Messages.FlowTargetUnsetCmd_WORKSPACE_NOT_FOUND, null);
    }
    
    WorkspaceDetailsDTO workspaceDetails = (WorkspaceDetailsDTO)result.get(0);
    List<WorkspaceComponentDTO> comps = workspaceDetails.getComponents();
    List<BaselineDTO> matchedBaselines = new ArrayList();
    BaselineDTO baseline; for (WorkspaceComponentDTO comp : comps) {
      baseline = comp.getBaseline();
      if ((blUUID != null) && (baseline.getItemId().equals(blUUID))) {
        itemID = blUUID;
        matchedBaselines.add(baseline);
        break; }
      if (baseline.getName().equals(blSelector)) {
        itemID = baseline.getItemId();
        matchedBaselines.add(baseline);
      }
    }
    
    if (matchedBaselines.size() == 0)
      throw StatusHelper.itemNotFound(NLS.bind(Messages.DiffCmd_12, blSelector));
    if (matchedBaselines.size() > 1) {
      List<SubcommandUtil.ItemInfo> blMatched = new ArrayList();
      for (BaselineDTO bl : matchedBaselines) {
        blMatched.add(new SubcommandUtil.ItemInfo(bl.getName(), bl.getItemId(), bl.getRepositoryURL(), RepoUtil.ItemType.BASELINE));
      }
      
      SubcommandUtil.displayAmbiguousSelectorException(blSelector, blMatched, config);
      throw StatusHelper.ambiguousSelector(NLS.bind(Messages.DiffCmd_15, blSelector));
    }
    return itemID;
  }
  
  private void listChanges(List<IScmCommandLineArgument> csSelectors, String itemID, String contextType, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli)
    throws FileSystemException
  {
    Map<ITeamRepository, List<String>> repoUuids = new HashMap();
    
    for (IScmCommandLineArgument csSelector : csSelectors) {
      ITeamRepository repoCs = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
      
      if ((repo != null) && (!repo.getId().getUuidValue().equals(repoCs.getId().getUuidValue()))) {
        ICommandLineArgument ctxtSelector = cli.hasOption(ListChangesCmdOptions.OPT_WORKSPACE) ? 
          cli.getOptionValue(ListChangesCmdOptions.OPT_WORKSPACE) : cli.getOptionValue(ListChangesCmdOptions.OPT_SNAPSHOT);
        IScmCommandLineArgument contextSelector = ScmCommandLineArgument.create(ctxtSelector, config);
        StatusHelper.repoIncorrectlySpecified(NLS.bind(Messages.ListChangesCmd_AMBIGUOUS_REPOS, 
          contextSelector.getItemSelector()));
      }
      
      SubcommandUtil.validateArgument(csSelector, RepoUtil.ItemType.CHANGESET);
      



      IUuidAliasRegistry.IUuidAlias csItem = RepoUtil.lookupUuidAndAlias(csSelector.getItemSelector());
      if (csItem == null) {
        throw StatusHelper.itemNotFound(NLS.bind("Unable to find change set ''{0}''", csSelector));
      }
      String csUuid = csItem.getUuid().getUuidValue();
      


      if (repoUuids.get(repoCs) == null) {
        List<String> selectors = new ArrayList();
        selectors.add(csUuid);
        repoUuids.put(repoCs, selectors);
      } else {
        ((List)repoUuids.get(repoCs)).add(csUuid);
      }
    }
    
    for (ITeamRepository repoItem : repoUuids.keySet()) {
      List<String> csIds = (List)repoUuids.get(repoItem);
      
      PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
      options.setVerbose(true);
      options.enablePrinter(20);
      options.enablePrinter(4);
      options.enablePrinter(6);
      options.enablePrinter(7);
      options.enablePrinter(31);
      options.enablePrinter(14);
      options.enablePrinter(19);
      options.enablePrinter(37);
      
      if (cli.hasOption(ListChangesCmdOptions.OPT_SHOW_FULL_VERSION_ID)) {
        options.enablePrinter(36);
      }
      
      if (cli.hasOption(ListChangesCmdOptions.OPT_SHOW_SHORT_VERSION_ID)) {
        options.enablePrinter(35);
      }
      
      if (cli.hasOption(ListChangesCmdOptions.OPT_INCLUDE_UNCHANGED_FILES)) {
        options.enablePrinter(41);
      }
      
      for (String csId : csIds) {
        options.addFilter(UUID.valueOf(csId), 4);
      }
      ChangeSetStateFactory stateFactory = ChangeSetStateFactory.createChangeSetstateFactory(client, repo, null, null);
      PendingChangesUtil.printChangeSets2(repoItem, csIds, stateFactory, options, 
        new IndentingPrintStream(config.getContext().stdout()), client, config, null);
    }
  }
}
