package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.util.ChangeSetUtil;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.ChangeSetSourceDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rest.dto.WorkspaceDTO;
import com.ibm.team.scm.common.internal.rest.dto.WorkspaceListDTO;
import com.ibm.team.scm.common.internal.rest.dto.WorkspaceListItemDTO;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSetLocationsEntry;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSetLocationsEntry2;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSetLocationsEntry3;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSetLocationsResult;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmPortInfo;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetChangeSetLocations;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;






public class ChangesetLocateCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ChangesetLocateCmd() {}
  
  public static final PositionalOptionDefinition OPT_SELECTORS = new PositionalOptionDefinition("selectors", 1, -1, "@");
  public static final NamedOptionDefinition OPT_CHANGESETS = new NamedOptionDefinition("C", "change-sets", 0);
  public static final NamedOptionDefinition OPT_WORKITEMS = new NamedOptionDefinition("W", "workitems", 0);
  public static final NamedOptionDefinition OPT_WORKITEMS_ALL = new NamedOptionDefinition(null, "include-child-workitems", 0);
  public static final NamedOptionDefinition OPT_WORKSPACES = new NamedOptionDefinition("w", "workspaces", -1, "@");
  public static final NamedOptionDefinition OPT_SNAPSHOTS = new NamedOptionDefinition("s", "snapshots", -1, "@");
  public static final NamedOptionDefinition OPT_BASELINES = new NamedOptionDefinition("b", "baselines", -1, "@");
  public static final NamedOptionDefinition OPT_PROJECTAREA = new NamedOptionDefinition(null, "projectarea", 1, "@");
  public static final NamedOptionDefinition OPT_TEAMAREA = new NamedOptionDefinition(null, "teamarea", 1, "@");
  public static final NamedOptionDefinition OPT_RELATED_CHANGESETS = new NamedOptionDefinition(null, "include-related", 0);
  
  protected ChangeSetUtil csUtil = new ChangeSetUtil();
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP)
      .addOption(OPT_RELATED_CHANGESETS, Messages.ChangesetLocateCmdOptions_OPT_RELATED_HELP)
      .addOption(new ContinuousGroup(true)
      .addOption(OPT_WORKSPACES, Messages.ChangesetLocateCmdOptions_WORKSPACE_HELP, false)
      .addOption(OPT_SNAPSHOTS, Messages.ChangesetLocateCmdOptions_SNAPSHOT_HELP, false)
      .addOption(OPT_BASELINES, Messages.ChangesetLocateCmd_BASELINE_HELP, false)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_PROJECTAREA, Messages.ChangesetLocateCmdOptions_PROJECTAREA_HELP, false)
      .addOption(OPT_TEAMAREA, Messages.ChangesetLocateCmdOptions_TEAMAREA_HELP, false)))
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_CHANGESETS, Messages.ChangesetLocateCmdOptions_OPT_CHANGESETS_HELP, false)
      .addOption(new SimpleGroup(false)
      .addOption(OPT_WORKITEMS, Messages.ChangesetLocateCmdOptions_OPT_WORKITEMS_HELP, true)
      .addOption(OPT_WORKITEMS_ALL, NLS.bind(Messages.ChangesetLocateCmdOptions_OPT_WORKITEMS_ALL_HELP, 
      OPT_WORKITEMS.getName()), false)))
      .addOption(CommonOptions.OPT_POSITIONAL_ARG_SEPARATOR, NLS.bind(
      Messages.PositionalArgSeparator_Help, OPT_SELECTORS.getName()))
      .addOption(OPT_SELECTORS, NLS.bind(Messages.ChangesetLocateCmdOptions_OPT_CS_WI_HELP, 
      OPT_CHANGESETS.getName(), OPT_WORKITEMS.getName()));
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    if ((!cli.hasOption(OPT_PROJECTAREA)) && (!cli.hasOption(OPT_TEAMAREA)) && (!cli.hasOption(OPT_BASELINES)) && 
      (!cli.hasOption(OPT_SNAPSHOTS)) && (!cli.hasOption(OPT_WORKSPACES))) {
      throw StatusHelper.argSyntax(Messages.ChangesetLocateCmd_SPECIFY_TARGET);
    }
    
    if ((cli.hasOption(OPT_PROJECTAREA)) && (cli.hasOption(OPT_TEAMAREA)))
    {
      throw StatusHelper.argSyntax(NLS.bind(
        Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        cli.getDefinition().getOption(OPT_PROJECTAREA).getName(), 
        cli.getDefinition().getOption(OPT_TEAMAREA).getName()));
    }
    
    if ((cli.hasOption(OPT_WORKITEMS_ALL)) && (!cli.hasOption(OPT_WORKITEMS))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ChangesetLocateCmd_WI_ALL_ONLY_FOR_WORKITEMS, 
        cli.getDefinition().getOption(OPT_WORKITEMS_ALL).getName(), 
        cli.getDefinition().getOption(OPT_WORKITEMS).getName()));
    }
    
    List<IScmCommandLineArgument> selectors;
    
    if (cli.hasOption(OPT_WORKITEMS))
    {
      List<IScmCommandLineArgument> selectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SELECTORS), config, false);
      SubcommandUtil.validateArgument(selectors, RepoUtil.ItemType.WORKITEM);
    } else {
      selectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SELECTORS), config);
      SubcommandUtil.validateArgument(selectors, RepoUtil.ItemType.CHANGESET);
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    
    if (cli.hasOption(CommonOptions.OPT_URI)) {
      String repoUri = cli.getOption(CommonOptions.OPT_URI);
      repo = RepoUtil.login(config, client, config.getConnectionInfo(repoUri));
      csUtil.repoStrToRepo.put(repoUri, repo);
    }
    

    validateArgumentRepoAndLogin(cli, client, config);
    

    Map<String, ChangeSetSyncDTO> csList = null;
    if (cli.hasOption(OPT_WORKITEMS)) {
      csList = csUtil.getChangeSetsFromWorkitem(selectors, repo, client, config, cli.hasOption(OPT_WORKITEMS_ALL));
      if ((csList == null) || (csList.size() == 0)) {
        config.getContext().stdout().println(Messages.ChangesetLocateCmd_CS_NOT_FOUND_FOR_WI);
      }
    }
    else {
      csList = csUtil.getChangeSets(selectors, repo, client, config);
    }
    

    Map<ITeamRepository, Map<String, IWorkspace>> repoToWsList = getWorkspaces(cli, repo, client, config);
    

    Map<ITeamRepository, Map<String, IBaselineSet>> repoToSsList = getSnapshots(cli, repo, client, config);
    

    Map<ITeamRepository, Map<String, IBaseline>> repoToBlList = getBaselines(cli, repo, client, config);
    

    boolean hasWs = false;
    boolean hasSs = false;
    boolean hasBl = false;
    for (Map.Entry<ITeamRepository, Map<String, IWorkspace>> entry : repoToWsList.entrySet()) {
      if ((entry.getValue() != null) && (((Map)entry.getValue()).size() > 0)) {
        hasWs = true;
        break;
      }
    }
    for (Map.Entry<ITeamRepository, Map<String, IBaselineSet>> entry : repoToSsList.entrySet()) {
      if ((entry.getValue() != null) && (((Map)entry.getValue()).size() > 0)) {
        hasSs = true;
        break;
      }
    }
    for (Map.Entry<ITeamRepository, Map<String, IBaseline>> entry : repoToBlList.entrySet()) {
      if ((entry.getValue() != null) && (((Map)entry.getValue()).size() > 0)) {
        hasBl = true;
        break;
      }
    }
    
    if ((!hasWs) && (!hasSs) && (!hasBl)) {
      config.getContext().stdout().println(Messages.ChangesetLocateCmd_NO_TARGETS_FOUND);
      return;
    }
    

    Set<ITeamRepository> repoList = new HashSet();
    if (repoToWsList.size() > 0) {
      repoList.addAll(repoToWsList.keySet());
    }
    if (repoToSsList.size() > 0) {
      repoList.addAll(repoToSsList.keySet());
    }
    if (repoToBlList.size() > 0) {
      repoList.addAll(repoToBlList.keySet());
    }
    

    Object repoToCsLocEntryList = new HashMap();
    Map<String, IBaselineSet> ssList; for (ITeamRepository locateInRepo : repoList) {
      Map<String, IWorkspace> wsList = (Map)repoToWsList.get(locateInRepo);
      ssList = (Map)repoToSsList.get(locateInRepo);
      Map<String, IBaseline> blList = (Map)repoToBlList.get(locateInRepo);
      List<ScmChangeSetLocationsEntry> csLocEntryList = locateChangeSets(csList.keySet(), wsList != null ? wsList.keySet() : null, 
        ssList != null ? ssList.keySet() : null, blList != null ? blList.keySet() : null, locateInRepo, config);
      if (csLocEntryList != null) {
        ((Map)repoToCsLocEntryList).put(locateInRepo, csLocEntryList);
      }
    }
    
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(4);
    
    if (cli.hasOption(OPT_RELATED_CHANGESETS)) {
      options.enablePrinter(31);
    }
    if (cli.hasOption(OPT_WORKITEMS)) {
      options.enablePrinter(22);
    }
    

    JSONArray itemArray = new JSONArray();
    for (Map.Entry<ITeamRepository, List<ScmChangeSetLocationsEntry>> entry : ((Map)repoToCsLocEntryList).entrySet()) {
      if (entry.getValue() != null) {
        Map<String, IWorkspace> wsList = (Map)repoToWsList.get(entry.getKey());
        Map<String, IBaselineSet> ssList = (Map)repoToSsList.get(entry.getKey());
        Map<String, IBaseline> blList = (Map)repoToBlList.get(entry.getKey());
        
        JSONArray repoItemArray = jsonizeCsLocEntries((List)entry.getValue(), ((ITeamRepository)entry.getKey()).getRepositoryURI(), 
          client, config, csList, wsList, ssList, blList, cli.hasOption(CommonOptions.OPT_VERBOSE), options);
        itemArray.addAll(repoItemArray);
      }
    }
    

    if (config.isJSONEnabled()) {
      if (itemArray.size() > 0) {
        JSONObject entriesObj = new JSONObject();
        entriesObj.put("entries", itemArray);
        config.getContext().stdout().print(entriesObj);
      }
    } else {
      IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
      printCsLocEntries(itemArray, config, out, cli.hasOption(CommonOptions.OPT_VERBOSE), options);
    }
  }
  
  private void validateArgumentRepoAndLogin(ICommandLine cli, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    List<IScmCommandLineArgument> csWiSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SELECTORS), config);
    csUtil.validateRepoAndLogin(csWiSelectors, client, config, cli, true);
    
    if (cli.hasOption(OPT_WORKSPACES)) {
      List<IScmCommandLineArgument> wsSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_WORKSPACES), config);
      csUtil.validateRepoAndLogin(wsSelectors, client, config, cli, true);
    }
    
    if (cli.hasOption(OPT_SNAPSHOTS)) {
      List<IScmCommandLineArgument> ssSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SNAPSHOTS), config);
      csUtil.validateRepoAndLogin(ssSelectors, client, config, cli, true);
    }
    
    if (cli.hasOption(OPT_BASELINES)) {
      List<IScmCommandLineArgument> blSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_BASELINES), config);
      csUtil.validateRepoAndLogin(blSelectors, client, config, cli, true);
    }
    
    IScmCommandLineArgument projAreaSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROJECTAREA, null), config);
    if (cli.hasOption(OPT_PROJECTAREA)) {
      csUtil.validateRepoAndLogin(Collections.singletonList(projAreaSelector), client, config, cli, true);
    }
    if (cli.hasOption(OPT_TEAMAREA)) {
      IScmCommandLineArgument teamAreaSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_TEAMAREA), config);
      csUtil.validateRepoAndLogin(Collections.singletonList(teamAreaSelector), client, config, cli, 
        projAreaSelector == null);
    }
  }
  
  private void getRelatedChangeSets(List<ScmChangeSetLocationsEntry> csLocEntryList, String repoUri, IFilesystemRestClient client, Map<String, ChangeSetSyncDTO> csList)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    if (cli.hasOption(OPT_RELATED_CHANGESETS))
    {
      List<String> csIdList = new ArrayList();
      String csId; if (csLocEntryList != null) {
        for (ScmChangeSetLocationsEntry csLocEntry : csLocEntryList) {
          csId = csLocEntry.getChangeSetItemId();
          if (!csList.containsKey(csId)) {
            csIdList.add(csId);
          }
        }
      }
      
      if (csIdList.size() > 0) {
        ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(csIdList, false, null, null, repoUri, client, config);
        if (csDTOList != null) { ChangeSetSyncDTO[] arrayOfChangeSetSyncDTO1;
          String str1 = (arrayOfChangeSetSyncDTO1 = csDTOList).length; for (csId = 0; csId < str1; csId++) { ChangeSetSyncDTO csDTO = arrayOfChangeSetSyncDTO1[csId];
            String csId = csDTO.getChangeSetItemId();
            if (!csList.containsKey(csId)) {
              csList.put(csId, csDTO);
            }
          }
        }
      }
    }
  }
  
  private Map<ITeamRepository, Map<String, IWorkspace>> getWorkspaces(ICommandLine cli, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Map<ITeamRepository, Map<String, IWorkspace>> repoToWsList = new HashMap();
    Map<String, IWorkspace> wsList;
    if (cli.hasOption(OPT_WORKSPACES)) {
      List<IScmCommandLineArgument> wsSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_WORKSPACES), config);
      SubcommandUtil.validateArgument(wsSelectors, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      

      Map<ITeamRepository, List<String>> repoToWsSelectors = new HashMap();
      for (IScmCommandLineArgument wsSelector : wsSelectors) {
        ITeamRepository wsRepo = repo;
        if (wsSelector.getRepositorySelector() != null) {
          wsRepo = (ITeamRepository)csUtil.repoStrToRepo.get(wsSelector.getRepositorySelector());
        }
        
        List<String> wsList = (List)repoToWsSelectors.get(wsRepo);
        if (wsList == null) {
          wsList = new ArrayList();
          repoToWsSelectors.put(wsRepo, wsList);
        }
        wsList.add(wsSelector.getItemSelector());
      }
      

      for (Map.Entry<ITeamRepository, List<String>> entry : repoToWsSelectors.entrySet()) {
        wsList = getWorkspacesFromRepo((List)entry.getValue(), (ITeamRepository)entry.getKey(), config);
        
        repoToWsList.put((ITeamRepository)entry.getKey(), wsList);
      }
    }
    
    if ((cli.hasOption(OPT_PROJECTAREA)) || (cli.hasOption(OPT_TEAMAREA))) {
      List<IWorkspace> workspaces = new ArrayList();
      ITeamRepository processAreaRepo = getWorkspacesFromProcessArea(cli, workspaces, repo, config);
      
      Map<String, IWorkspace> wsList = (Map)repoToWsList.get(processAreaRepo);
      if (wsList == null) {
        wsList = new HashMap();
        repoToWsList.put(processAreaRepo, wsList);
      }
      
      for (IWorkspace ws : workspaces) {
        if (!wsList.containsKey(ws.getItemId().getUuidValue())) {
          wsList.put(ws.getItemId().getUuidValue(), ws);
        }
        
        if (wsList.keySet().size() >= 512) {
          break;
        }
      }
    }
    
    return repoToWsList;
  }
  
  private Map<String, IWorkspace> getWorkspacesFromRepo(List<String> wsSelectors, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<String, IWorkspace> wsList = new HashMap();
    
    for (String wsSelector : wsSelectors)
    {
      WorkspaceListDTO result = RepoUtil.getWorkspacesByName(wsSelector, true, true, true, 
        512, repo, config);
      
      if (result != null) {
        for (WorkspaceListItemDTO wsItem : result.getItems()) {
          IWorkspace ws = wsItem.getWorkspace();
          if (!wsList.containsKey(ws.getItemId().getUuidValue())) {
            wsList.put(ws.getItemId().getUuidValue(), ws);
          }
        }
      }
      

      IUuidAliasRegistry.IUuidAlias wsAlias = RepoUtil.lookupUuidAndAlias(wsSelector, repo.getRepositoryURI());
      if (wsAlias != null) {
        WorkspaceDTO wsDTO = RepoUtil.getWorkspaceById(wsAlias.getUuid().getUuidValue(), repo, config);
        if (wsDTO != null) {
          IWorkspace ws = wsDTO.getWorkspace();
          if (!wsList.containsKey(ws.getItemId().getUuidValue())) {
            wsList.put(ws.getItemId().getUuidValue(), ws);
          }
        }
      }
      
      if (wsList.keySet().size() == 0) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ChangesetLocateCmd_WORKSPACE_NOT_FOUND, wsSelector));
      }
      

      if (wsList.keySet().size() >= 512) {
        break;
      }
    }
    
    return wsList;
  }
  
  private ITeamRepository getWorkspacesFromProcessArea(ICommandLine cli, List<IWorkspace> workspaces, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    IProjectArea projectArea = null;
    ITeamRepository processAreaRepo = repo;
    if (cli.hasOption(OPT_PROJECTAREA))
    {
      IScmCommandLineArgument projAreaSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROJECTAREA), config);
      if (projAreaSelector.getRepositorySelector() != null) {
        processAreaRepo = (ITeamRepository)csUtil.repoStrToRepo.get(projAreaSelector.getRepositorySelector());
      }
      projectArea = RepoUtil.getProjectArea(processAreaRepo, projAreaSelector.getItemSelector(), config);
      if (projectArea == null) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ListCmd_NOPROJECTAREA, 
          projAreaSelector.getItemSelector()));
      }
    }
    
    ITeamArea teamArea = null;
    if (cli.hasOption(OPT_TEAMAREA)) {
      IScmCommandLineArgument teamAreaSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_TEAMAREA), config);
      if (processAreaRepo == null) {
        processAreaRepo = repo;
        if (teamAreaSelector.getRepositorySelector() != null) {
          processAreaRepo = (ITeamRepository)csUtil.repoStrToRepo.get(teamAreaSelector.getRepositorySelector());
        }
      }
      teamArea = RepoUtil.getTeamArea(teamAreaSelector.getItemSelector(), projectArea, config, processAreaRepo);
      if (teamArea == null) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ListCmd_TeamAreaNotFound, teamAreaSelector.getItemSelector()));
      }
    }
    
    RepoUtil.getWorkspacesByName("*", projectArea, teamArea, null, 512, 
      false, true, false, processAreaRepo, workspaces, config);
    
    return processAreaRepo;
  }
  
  private Map<ITeamRepository, Map<String, IBaselineSet>> getSnapshots(ICommandLine cli, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Map<ITeamRepository, Map<String, IBaselineSet>> repoToSsList = new HashMap();
    
    if (cli.hasOption(OPT_SNAPSHOTS)) {
      List<IScmCommandLineArgument> ssSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SNAPSHOTS), config);
      SubcommandUtil.validateArgument(ssSelectors, RepoUtil.ItemType.SNAPSHOT);
      

      Map<ITeamRepository, List<String>> repoToSsSelectors = new HashMap();
      for (IScmCommandLineArgument ssSelector : ssSelectors) {
        ITeamRepository ssRepo = repo;
        if (ssSelector.getRepositorySelector() != null) {
          ssRepo = (ITeamRepository)csUtil.repoStrToRepo.get(ssSelector.getRepositorySelector());
        }
        
        List<String> ssList = (List)repoToSsSelectors.get(ssRepo);
        if (ssList == null) {
          ssList = new ArrayList();
          repoToSsSelectors.put(ssRepo, ssList);
        }
        ssList.add(ssSelector.getItemSelector());
      }
      

      for (Map.Entry<ITeamRepository, List<String>> entry : repoToSsSelectors.entrySet()) {
        Map<String, IBaselineSet> ssList = getSnapshotsFromRepo((List)entry.getValue(), (ITeamRepository)entry.getKey(), config);
        
        repoToSsList.put((ITeamRepository)entry.getKey(), ssList);
      }
    }
    

    return repoToSsList;
  }
  
  private Map<String, IBaselineSet> getSnapshotsFromRepo(List<String> ssSelectors, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    Map<String, IBaselineSet> ssList = new HashMap();
    
    for (String ssSelector : ssSelectors)
    {
      List<IBaselineSet> result = RepoUtil.getSnapshotByName(null, ssSelector, 
        false, 512, repo, config);
      if ((result != null) && (result.size() > 0)) {
        for (IBaselineSet ssItem : result) {
          ssList.put(ssItem.getItemId().getUuidValue(), ssItem);
        }
      }
      

      IUuidAliasRegistry.IUuidAlias ssAlias = RepoUtil.lookupUuidAndAlias(ssSelector, repo.getRepositoryURI());
      if (ssAlias != null) {
        IBaselineSet ss = null;
        try {
          ss = RepoUtil.getSnapshotById(ssAlias.getUuid().getUuidValue(), repo, config);
        }
        catch (Exception localException) {}
        
        if ((ss != null) && (!ssList.containsKey(ss.getItemId().getUuidValue()))) {
          ssList.put(ss.getItemId().getUuidValue(), ss);
        }
      }
      
      if (ssList.keySet().size() == 0) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ChangesetLocateCmd_SNAPSHOT_NOT_FOUND, ssSelector));
      }
      

      if (ssList.keySet().size() >= 512) {
        break;
      }
    }
    
    return ssList;
  }
  
  private Map<ITeamRepository, Map<String, IBaseline>> getBaselines(ICommandLine cli, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<ITeamRepository, Map<String, IBaseline>> repoToBlList = new HashMap();
    
    if (cli.hasOption(OPT_BASELINES)) {
      List<IScmCommandLineArgument> blSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_BASELINES), config);
      SubcommandUtil.validateArgument(blSelectors, RepoUtil.ItemType.BASELINE);
      

      Map<ITeamRepository, List<String>> repoToBlSelectors = new HashMap();
      for (IScmCommandLineArgument blSelector : blSelectors) {
        ITeamRepository blRepo = repo;
        if (blSelector.getRepositorySelector() != null) {
          blRepo = (ITeamRepository)csUtil.repoStrToRepo.get(blSelector.getRepositorySelector());
        }
        
        List<String> blList = (List)repoToBlSelectors.get(blRepo);
        if (blList == null) {
          blList = new ArrayList();
          repoToBlSelectors.put(blRepo, blList);
        }
        blList.add(blSelector.getItemSelector());
      }
      

      for (Map.Entry<ITeamRepository, List<String>> entry : repoToBlSelectors.entrySet()) {
        Map<String, IBaseline> blList = getBaselinesFromRepo((List)entry.getValue(), (ITeamRepository)entry.getKey(), client, config);
        repoToBlList.put((ITeamRepository)entry.getKey(), blList);
      }
    }
    
    return repoToBlList;
  }
  
  private Map<String, IBaseline> getBaselinesFromRepo(List<String> baselineSelectors, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<String, IBaseline> blList = new HashMap();
    
    for (String blSelector : baselineSelectors)
    {
      IUuidAliasRegistry.IUuidAlias blAlias = RepoUtil.lookupUuidAndAlias(blSelector, repo.getRepositoryURI());
      if (blAlias != null) {
        IBaseline baseline = null;
        try {
          BaselineDTO blDTO = RepoUtil.getBaselineById(blAlias.getUuid().getUuidValue(), 
            repo.getRepositoryURI(), client, config);
          if (blDTO != null) {
            baseline = (IBaseline)RepoUtil.getItem(IBaseline.ITEM_TYPE, UUID.valueOf(blDTO.getItemId()), 
              repo, config);
          }
        }
        catch (Exception localException) {}
        

        if ((baseline != null) && (!blList.containsKey(baseline.getItemId().getUuidValue()))) {
          blList.put(baseline.getItemId().getUuidValue(), baseline);
        }
      }
      
      if (blList.keySet().size() == 0) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ChangesetLocateCmd_BASELINE_NOT_FOUND, blSelector));
      }
      

      if (blList.keySet().size() >= 512) {
        break;
      }
    }
    
    return blList;
  }
  
  private List<ScmChangeSetLocationsEntry> locateChangeSets(Set<String> csIds, Set<String> wsIds, Set<String> ssIds, Set<String> blIds, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
      .getServiceInterface(IScmRichClientRestService.class);
    
    IScmRichClientRestService.ParmsGetChangeSetLocations parms = new IScmRichClientRestService.ParmsGetChangeSetLocations();
    changeSetItemIds = ((String[])csIds.toArray(new String[csIds.size()]));
    if ((wsIds != null) && (wsIds.size() > 0)) {
      workspaceItemIds = ((String[])wsIds.toArray(new String[wsIds.size()]));
    }
    if ((ssIds != null) && (ssIds.size() > 0)) {
      snapshotItemIds = ((String[])ssIds.toArray(new String[ssIds.size()]));
    }
    if ((blIds != null) && (blIds.size() > 0)) {
      baselineItemIds = ((String[])blIds.toArray(new String[blIds.size()]));
    }
    
    ICommandLine cli = config.getSubcommandCommandLine();
    includePorts = cli.hasOption(OPT_RELATED_CHANGESETS);
    
    ScmChangeSetLocationsResult result = null;
    try {
      result = scmService.getChangeSetLocations(parms);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ListChangesetsCmd_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    if (result != null) {
      return result.getEntries();
    }
    return null;
  }
  




  private JSONArray jsonizeCsLocEntries(List<ScmChangeSetLocationsEntry> csLocEntryList, String repoUri, IFilesystemRestClient client, IScmClientConfiguration config, Map<String, ChangeSetSyncDTO> csList, Map<String, IWorkspace> wsList, Map<String, IBaselineSet> ssList, Map<String, IBaseline> blList, boolean verbose, PendingChangesUtil.PendingChangesOptions options)
    throws FileSystemException
  {
    Map<String, List<String>> wsToCsMap = new HashMap();
    Map<String, List<String>> ssToCsMap = new HashMap();
    Map<String, List<String>> blToCsMap = new HashMap();
    
    Map<String, List<String>> wsToRelCsMap = new HashMap();
    Map<String, List<String>> ssToRelCsMap = new HashMap();
    Map<String, List<String>> blToRelCsMap = new HashMap();
    
    Map<String, List<String>> wsToIncCsMap = new HashMap();
    Map<String, List<String>> ssToIncCsMap = new HashMap();
    Map<String, List<String>> blToIncCsMap = new HashMap();
    for (ScmChangeSetLocationsEntry csLocEntry : csLocEntryList) {
      groupChangeSets(csLocEntry.getWorkspaceItemIds(), wsToCsMap, wsToRelCsMap, wsToIncCsMap, csLocEntry, csList, csLocEntryList);
      groupChangeSets(csLocEntry.getSnapshotItemIds(), ssToCsMap, ssToRelCsMap, ssToIncCsMap, csLocEntry, csList, csLocEntryList);
      if ((csLocEntry instanceof ScmChangeSetLocationsEntry3)) {
        groupChangeSets(((ScmChangeSetLocationsEntry3)csLocEntry).getBaselineItemIds(), blToCsMap, blToRelCsMap, blToIncCsMap, csLocEntry, csList, csLocEntryList);
      }
    }
    

    if (wsList != null) {
      for (Map.Entry<String, IWorkspace> entry : wsList.entrySet()) {
        fillEmptyTarget((String)entry.getKey(), wsToCsMap, wsToRelCsMap);
      }
    }
    
    if (ssList != null) {
      for (Map.Entry<String, IBaselineSet> entry : ssList.entrySet()) {
        fillEmptyTarget((String)entry.getKey(), ssToCsMap, ssToRelCsMap);
      }
    }
    
    if (blList != null) {
      for (Map.Entry<String, IBaseline> entry : blList.entrySet()) {
        fillEmptyTarget((String)entry.getKey(), blToCsMap, blToRelCsMap);
      }
    }
    

    JSONArray itemArray = new JSONArray();
    int totalCs = csList.size();
    
    getRelatedChangeSets(csLocEntryList, repoUri, client, csList);
    

    for (Map.Entry<String, List<String>> entry : wsToCsMap.entrySet()) {
      String wsId = (String)entry.getKey();
      if ((wsList != null) && (wsList.containsKey(wsId))) {
        IWorkspace ws = (IWorkspace)wsList.get(wsId);
        
        JSONObject itemObj = jsonizeItem(client, config, ws.getName(), ws.getItemId().getUuidValue(), repoUri, totalCs, 
          (List)entry.getValue(), (List)wsToRelCsMap.get(wsId), (List)wsToIncCsMap.get(wsId), csList, ws.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE, verbose, options);
        itemArray.add(itemObj);
      }
    }
    

    for (Map.Entry<String, List<String>> entry : ssToCsMap.entrySet()) {
      String ssId = (String)entry.getKey();
      if ((ssList != null) && (ssList.containsKey(ssId))) {
        IBaselineSet ss = (IBaselineSet)ssList.get(ssId);
        
        JSONObject itemObj = jsonizeItem(client, config, ss.getName(), ss.getItemId().getUuidValue(), repoUri, 
          totalCs, (List)entry.getValue(), (List)ssToRelCsMap.get(ssId), (List)ssToIncCsMap.get(ssId), csList, RepoUtil.ItemType.SNAPSHOT, verbose, options);
        itemArray.add(itemObj);
      }
    }
    

    for (Map.Entry<String, List<String>> entry : blToCsMap.entrySet()) {
      String blId = (String)entry.getKey();
      if ((blList != null) && (blList.containsKey(blId))) {
        IBaseline bl = (IBaseline)blList.get(blId);
        
        JSONObject itemObj = jsonizeItem(client, config, bl.getName(), bl.getItemId().getUuidValue(), repoUri, 
          totalCs, (List)entry.getValue(), (List)ssToRelCsMap.get(blId), (List)ssToIncCsMap.get(blId), csList, RepoUtil.ItemType.BASELINE, verbose, options);
        itemArray.add(itemObj);
      }
    }
    
    return itemArray;
  }
  
  private void groupChangeSets(List<String> targetIds, Map<String, List<String>> targetToCsMap, Map<String, List<String>> targetToRelCsMap, Map<String, List<String>> targetToIncCsMap, ScmChangeSetLocationsEntry csLocEntry, Map<String, ChangeSetSyncDTO> csList, List<ScmChangeSetLocationsEntry> csLocEntryList)
  {
    for (String targetId : targetIds) {
      List<String> csIdList = (List)targetToCsMap.get(targetId);
      List<String> relCsIdList = (List)targetToRelCsMap.get(targetId);
      List<String> incCsIdList = (List)targetToIncCsMap.get(targetId);
      if (csIdList == null) {
        csIdList = new ArrayList();
        targetToCsMap.put(targetId, csIdList);
      }
      if (relCsIdList == null) {
        relCsIdList = new ArrayList();
        targetToRelCsMap.put(targetId, relCsIdList);
      }
      if (incCsIdList == null) {
        incCsIdList = new ArrayList();
        targetToIncCsMap.put(targetId, incCsIdList);
      }
      
      String portId = null;
      if ((csLocEntry instanceof ScmChangeSetLocationsEntry2)) {
        ScmChangeSetLocationsEntry2 csLocEntry2 = (ScmChangeSetLocationsEntry2)csLocEntry;
        if (csLocEntry2.getPortInfo() != null) {
          portId = csLocEntry2.getPortInfo().getOriginalPortId();
        }
      }
      
      String csId = csLocEntry.getChangeSetItemId();
      if ((csList.containsKey(csId)) && 
        (!csIdList.contains(csId))) {
        csIdList.add(csId);
      }
      

      for (ScmChangeSetLocationsEntry resCsLocEntry : csLocEntryList) {
        if ((resCsLocEntry instanceof ScmChangeSetLocationsEntry2)) {
          ScmChangeSetLocationsEntry2 resCsLocEntry2 = (ScmChangeSetLocationsEntry2)resCsLocEntry;
          String relPortId = null;
          if (resCsLocEntry2.getPortInfo() != null) {
            relPortId = resCsLocEntry2.getPortInfo().getOriginalPortId();
          }
          String relCsId = resCsLocEntry2.getChangeSetItemId();
          if ((portId != null) && (portId.equals(relPortId)) && 
            (csList.containsKey(relCsId)) && (!csIdList.contains(relCsId)) && 
            (!relCsIdList.contains(relCsId))) {
            relCsIdList.add(relCsId);
          }
        }
      }
      


      if (!incCsIdList.contains(csId)) {
        incCsIdList.add(csId);
      }
    }
  }
  
  private void fillEmptyTarget(String targetId, Map<String, List<String>> targetToCsMap, Map<String, List<String>> targetToRelCsMap) {
    if (!targetToCsMap.containsKey(targetId)) {
      targetToCsMap.put(targetId, new ArrayList());
    }
    if (!targetToRelCsMap.containsKey(targetId)) {
      targetToRelCsMap.put(targetId, new ArrayList());
    }
  }
  
  private JSONObject jsonizeItem(IFilesystemRestClient client, IScmClientConfiguration config, String itemName, String itemUuid, String repoUri, int totalCs, List<String> csMatchedList, List<String> relCsMatchedList, List<String> incCsList, Map<String, ChangeSetSyncDTO> csList, RepoUtil.ItemType itemType, boolean verbose, PendingChangesUtil.PendingChangesOptions options)
    throws FileSystemException
  {
    JSONObject itemObj = new JSONObject();
    itemObj.put("uuid", itemUuid);
    itemObj.put("url", repoUri);
    itemObj.put("name", itemName);
    itemObj.put("type", itemType.toString());
    itemObj.put("items-total", Integer.toString(totalCs));
    itemObj.put("items-matched", Integer.toString(csMatchedList.size()));
    
    ICommandLine cli = config.getSubcommandCommandLine();
    boolean includeRelated = cli.hasOption(OPT_RELATED_CHANGESETS);
    if (includeRelated) {
      itemObj.put("rel-items-matched", Integer.toString(relCsMatchedList.size()));
    }
    

    if ((verbose) || (config.isJSONEnabled())) {
      JSONArray csArray = new JSONArray();
      JSONObject csObj; for (String cs : csMatchedList) {
        csObj = new JSONObject();
        JSONPrintUtil.jsonizeChangeSetHeader(csObj, (ChangeSetSyncDTO)csList.get(cs), new ChangeSetStateFactory(), options, config);
        csArray.add(csObj);
      }
      
      itemObj.put("changesets", csArray);
      
      if (includeRelated) {
        Map<String, ChangeSetSyncDTO> incCsDTOList = new HashMap();
        ChangeSetSyncDTO incCsDTO; for (String incCs : incCsList) {
          incCsDTO = (ChangeSetSyncDTO)csList.get(incCs);
          if (incCsDTO != null) {
            incCsDTOList.put(incCs, incCsDTO);
          }
        }
        JSONArray relCsArray = new JSONArray();
        for (String relCs : relCsMatchedList) {
          JSONObject relCsObj = new JSONObject();
          ChangeSetSyncDTO relCsDTO = (ChangeSetSyncDTO)csList.get(relCs);
          JSONPrintUtil.jsonizeChangeSetHeader(relCsObj, relCsDTO, new ChangeSetStateFactory(), options, config);
          relCsObj.put("related", jsonizeRelatedItem(relCsDTO, incCsDTOList, options));
          relCsArray.add(relCsObj);
        }
        
        itemObj.put("rel-changesets", relCsArray);
      }
    }
    
    return itemObj;
  }
  
  private JSONObject jsonizeRelatedItem(ChangeSetSyncDTO csDTO, Map<String, ChangeSetSyncDTO> relCsList, PendingChangesUtil.PendingChangesOptions options)
  {
    JSONObject relCsObj = new JSONObject();
    
    ChangeSetSourceDTO csSourceDTO = csDTO.getOriginalSource();
    String csSourceId = csSourceDTO.getSourceId();
    
    for (ChangeSetSyncDTO relCsDTO : relCsList.values())
    {
      ChangeSetSourceDTO relCsSourceDTO = relCsDTO.getOriginalSource();
      if (relCsSourceDTO != null) {
        String relSourceId = relCsSourceDTO.getSourceId();
        if (csSourceId.equals(relSourceId)) {
          JSONPrintUtil.jsonizeChangeSetHeader(relCsObj, relCsDTO, new ChangeSetStateFactory(), options, config);
          break;
        }
      }
    }
    return relCsObj;
  }
  
  private void printCsLocEntries(JSONArray itemArray, IClientConfiguration config, IndentingPrintStream out, boolean verbose, PendingChangesUtil.PendingChangesOptions options) {
    for (Object itemObj : itemArray) {
      JSONObject item = (JSONObject)itemObj;
      
      String matchedCS = NLS.bind(Messages.ChangesetLocateCmd_DISPLAY_CS_MATCH, 
        (String)item.get("items-matched"), (String)item.get("items-total"));
      
      String matchedRelCS = "";
      ICommandLine cli = config.getSubcommandCommandLine();
      boolean includeRelated = cli.hasOption(OPT_RELATED_CHANGESETS);
      if (includeRelated) {
        String numRelCs = (String)item.get("rel-items-matched");
        if (numRelCs != null) {
          matchedRelCS = NLS.bind(Messages.ChangesetLocateCmd_DISPLAY_REL_CS_MATCH, 
            numRelCs, (String)item.get("items-total"));
        }
      }
      
      out.println(AliasUtil.selector((String)item.get("name"), 
        UUID.valueOf((String)item.get("uuid")), (String)item.get("url"), 
        RepoUtil.ItemType.valueOf((String)item.get("type"))) + " " + matchedCS + 
        " " + matchedRelCS);
      
      if (verbose) {
        IndentingPrintStream csOut = out.indent();
        
        JSONArray csArray = (JSONArray)item.get("changesets");
        JSONObject cs; for (Object csObj : csArray) {
          cs = (JSONObject)csObj;
          PendingChangesUtil.printChangeSetHeader(cs, (String)item.get("url"), options, csOut);
        }
        
        if (includeRelated) {
          JSONArray relCsArray = (JSONArray)item.get("rel-changesets");
          for (Object csObj : relCsArray) {
            JSONObject cs = (JSONObject)csObj;
            PendingChangesUtil.printChangeSetHeader(cs, (String)item.get("url"), options, out.indent());
            
            JSONObject relCs = (JSONObject)cs.get("related");
            PendingChangesUtil.printChangeSetHeader(relCs, (String)item.get("url"), 
              Messages.ChangesetLocateCmd_RELATED, null, options, csOut.indent());
          }
        }
      }
    }
  }
}
