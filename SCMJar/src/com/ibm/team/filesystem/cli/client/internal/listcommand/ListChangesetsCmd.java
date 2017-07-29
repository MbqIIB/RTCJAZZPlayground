package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.util.ChangeSetUtil;
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
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.workitems.internal.hierarchy.WorkItemHierarchyNodeDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.IScmRestService.ParmsChangeSetSearchCriteria;
import com.ibm.team.scm.common.internal.rest.dto.ItemQueryPageDTO;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;


public class ListChangesetsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListChangesetsCmd() {}
  
  public static final IOptionKey OPT_WORKSPACE = new OptionKey("workspace");
  public static final IOptionKey OPT_COMPONENT = new OptionKey("component");
  public static final IOptionKey OPT_BASELINE = new OptionKey("baseline");
  public static final IOptionKey OPT_SUSPENDEDBY = new OptionKey("suspendedby");
  public static final IOptionKey OPT_CREATOR = new OptionKey("creator");
  public static final IOptionKey OPT_CREATED_AFTER = new OptionKey("created-after");
  public static final IOptionKey OPT_CREATED_BEFORE = new OptionKey("created-before");
  public static final IOptionKey OPT_CHANGE_PATH = new OptionKey("path");
  public static final IOptionKey OPT_CHANGE_NAME = new OptionKey("name");
  public static final IOptionKey OPT_CHANGE_TYPE = new OptionKey("type");
  public static final NamedOptionDefinition OPT_WORKITEMS = new NamedOptionDefinition("W", "workitems", 1, -1, "@");
  public static final NamedOptionDefinition OPT_WORKITEMS_ALL = new NamedOptionDefinition(null, "include-child-workitems", 0);
  
  private static final int MAX_FIND_CHANGESETS_LENGTH = 512;
  
  protected ChangeSetUtil csUtil = new ChangeSetUtil();
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP, false)
      .addOption(OPT_SUSPENDEDBY, null, "suspendedby", Messages.ListChangesetsCmdOption_SUSPENDEDBY, 1, false)
      .addOption(OPT_CREATOR, null, "creator", Messages.ListChangesetsCmdOption_CREATOR, 1, false)
      .addOption(OPT_CREATED_AFTER, null, "created-after", Messages.ListChangesetsCmdOption_CREATED_AFTER, 1, false)
      .addOption(OPT_CREATED_BEFORE, null, "created-before", Messages.ListChangesetsCmdOption_CREATED_BEFORE, 1, false)
      .addOption(OPT_CHANGE_PATH, "p", "path", Messages.ListChangesetsCmdOption_CHANGE_PATH, 1, false)
      .addOption(OPT_CHANGE_NAME, "n", "name", Messages.ListChangesetsCmdOption_CHANGE_NAME, 1, false)
      .addOption(OPT_CHANGE_TYPE, "t", "type", Messages.ListChangesetsCmdOption_CHANGE_TYPE, 1, false)
      .addOption(OPT_WORKSPACE, "w", "workspace", Messages.ListChangesetsCmdOption_WORKSPACE, 1, false)
      .addOption(new SimpleGroup(false)
      .addOption(new NamedOptionDefinition(OPT_COMPONENT, "C", "component", 1), CommonOptions.OPT_COMPONENT_SELECTOR_HELP, true)
      .addOption(new NamedOptionDefinition(OPT_BASELINE, "b", "baseline", 1), Messages.ListChangesetsCmdOption_BASELINE, false)))
      .addOption(new ContinuousGroup()
      .addOption(OPT_WORKITEMS, Messages.ListChangesetsCmdOption_WORKITEMS, true)
      .addOption(OPT_WORKITEMS_ALL, NLS.bind(Messages.ListChangesetsCmdOption_WORKITEMS_ALL, OPT_WORKITEMS.getName()), false));
    

    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    validateArguments(cli);
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(4);
    
    if (cli.hasOption(OPT_WORKITEMS)) {
      ITeamRepository repo = null;
      try {
        repo = RepoUtil.loginUrlArgAnc(config, client);
        csUtil.repoStrToRepo.put(repo.getRepositoryURI(), repo);
      }
      catch (FileSystemException localFileSystemException) {}
      



      List<IScmCommandLineArgument> selectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_WORKITEMS), config, false);
      csUtil.validateRepoAndLogin(selectors, client, config, cli, repo == null);
      
      List<WorkItemHierarchyNodeDTO> wiHierarchyList = new ArrayList();
      Map<String, ChangeSetSyncDTO> csList = csUtil.getChangeSetsFromWorkitem(selectors, repo, client, config, 
        cli.hasOption(OPT_WORKITEMS_ALL), wiHierarchyList);
      
      if ((csList == null) || (csList.size() == 0)) {
        config.getContext().stdout().println(Messages.ChangesetLocateCmd_CS_NOT_FOUND_FOR_WI);
        return;
      }
      

      PendingChangesUtil.printWorkItemHierarchyList(wiHierarchyList, csList, options, out, client, config);
    }
    else {
      IScmRestService.ParmsChangeSetSearchCriteria csCriteria = new IScmRestService.ParmsChangeSetSearchCriteria();
      ITeamRepository repo = generateParms(csCriteria, cli, client, config);
      
      IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
      List<String> csIds = new ArrayList();
      boolean hasMoreItems = getChangeSets(scmService, csCriteria, csIds, repo.getRepositoryURI(), 
        RepoUtil.getMaxResultsOption(cli), config, client, cli);
      
      if ((csIds.size() == 0) && (!config.isJSONEnabled())) {
        out.println(Messages.ListChangesetsCmd_NO_CS_FOUND);
      }
      

      String workspaceId = null;
      if ((contextType != null) && (contextType.equals("workspace"))) {
        workspaceId = contextHandleItemId;
      }
      ChangeSetStateFactory stateFactory = ChangeSetStateFactory.createChangeSetstateFactory(client, repo, workspaceId, componentItemId);
      PendingChangesUtil.printChangeSets2(repo, csIds, stateFactory, options, out, client, config, null);
      
      if ((hasMoreItems) && (!cli.hasOption(CommonOptions.OPT_MAXRESULTS)) && (!config.isJSONEnabled())) {
        config.getContext().stdout().println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, 
          cli.getDefinition().getOption(CommonOptions.OPT_MAXRESULTS).getName()));
      }
    }
  }
  







  public static boolean getChangeSets(IScmRestService scmService, IScmRestService.ParmsChangeSetSearchCriteria csCriteria, List<String> csIds, String repoUri, int maxChangeSets, IScmClientConfiguration config, IFilesystemRestClient client, ICommandLine cli)
    throws FileSystemException
  {
    int startIndex = 0;
    int maxResults = maxChangeSets;
    int fetchCsLength = Math.min(512, maxResults);
    length = Integer.valueOf(fetchCsLength);
    
    boolean hasMoreItems = true;
    ItemQueryPageDTO result;
    for (;;) {
      result = null;
      try
      {
        result = scmService.getFindChangeSets(csCriteria);
      } catch (TeamRepositoryException e) {
        if (startIndex == 0) {
          throw StatusHelper.wrap(Messages.ListChangesetsCmd_FAILURE, e, 
            new IndentingPrintStream(config.getContext().stderr()), repoUri);
        }
      }
      


      if (result == null) {
        hasMoreItems = false;
        break label411;
      }
      int size = result.getResultItemIds().size();
      if (size == 0) {
        hasMoreItems = false;
        
        break label411;
      }
      if (csIds.size() + size > maxResults) break;
      List<String> itemIds = result.getResultItemIds();
      csIds.addAll(itemIds);
      
      try
      {
        ChangeSetSyncDTO lastChangeSet = RepoUtil.findChangeSet(
          (String)itemIds.get(size - 1), 
          false, null, null, 
          repoUri, client, config);
        modifiedBeforeTimestamp = Long.valueOf(lastChangeSet.getLastChangeDate());
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.ListChangesetsCmd_FAILURE, e, 
          new IndentingPrintStream(config.getContext().stderr()), repoUri);
      }
      
      startIndex += size;
      fetchCsLength = Math.min(maxResults - startIndex, 512);
      if (fetchCsLength <= 0) {
        hasMoreItems = false;
        if (cli.hasOption(CommonOptions.OPT_MAXRESULTS))
          break label411;
        length = Integer.valueOf(1);
        try {
          result = scmService.getFindChangeSets(csCriteria);
        } catch (TeamRepositoryException e) {
          if (startIndex == 0) {
            throw StatusHelper.wrap(Messages.ListChangesetsCmd_FAILURE, e, 
              new IndentingPrintStream(config.getContext().stderr()), repoUri);
          }
        }
        if (result.getResultItemIds().size() <= 0) break label411;
        hasMoreItems = true;
        
        break label411;
      }
      
      length = Integer.valueOf(fetchCsLength);
    }
    
    List<String> itemsIds = result.getResultItemIds();
    int i = 0; for (int max = maxResults - csIds.size(); i < max; i++) {
      csIds.add((String)itemsIds.get(i));
    }
    hasMoreItems = false;
    

    label411:
    
    return hasMoreItems;
  }
  
  private ITeamRepository generateParms(IScmRestService.ParmsChangeSetSearchCriteria csCriteria, ICommandLine cli, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE, null), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository repo = null;
    

    if (cli.hasOption(OPT_SUSPENDEDBY)) {
      wsSelector = null;
    }
    

    ParmsWorkspace ws = null;
    if (wsSelector != null)
    {
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
      
      contextHandleItemId = workspaceItemId;
      contextType = "workspace";
    }
    

    IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT, null), config);
    SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    
    IComponent comp = null;
    if (compSelector != null) {
      if (ws == null) {
        repo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
      } else {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, Collections.singletonList(compSelector), 
          repo, config);
      }
      comp = getComponent(compSelector, ws, repo, client, config);
      componentItemId = comp.getItemId().getUuidValue();
    }
    

    IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_BASELINE, null), config);
    SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
    
    if (blSelector != null) {
      RepoUtil.validateItemRepos(RepoUtil.ItemType.BASELINE, Collections.singletonList(blSelector), repo, config);
      IBaseline blId = RepoUtil.getBaseline(blSelector.getItemSelector(), comp.getItemId().getUuidValue(), 
        comp.getName(), repo, client, config);
      contextHandleItemId = blId.getItemId().getUuidValue();
      contextType = "baseline";
    }
    

    if (repo == null) {
      repo = RepoUtil.loginUrlArgAnc(config, client);
    }
    

    IScmCommandLineArgument suspBySelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SUSPENDEDBY, null), config);
    SubcommandUtil.validateArgument(suspBySelector, RepoUtil.ItemType.CONTRIBUTOR);
    
    IContributor suspByContrib = null;
    if (suspBySelector != null) {
      suspByContrib = RepoUtil.getContributor(suspBySelector.getItemSelector(), repo, config);
      suspendedByContributorItemId = suspByContrib.getItemId().getUuidValue();
    }
    

    IScmCommandLineArgument creatorSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_CREATOR, null), config);
    SubcommandUtil.validateArgument(creatorSelector, RepoUtil.ItemType.CONTRIBUTOR);
    
    IContributor creatorContrib = null;
    if (creatorSelector != null) {
      creatorContrib = RepoUtil.getContributor(creatorSelector.getItemSelector(), repo, config);
      authorContributorItemId = creatorContrib.getItemId().getUuidValue();
    }
    

    Date dateAfter = null;
    String afterSelector = cli.getOption(OPT_CREATED_AFTER, null);
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
    if (afterSelector != null) {
      dateAfter = SubcommandUtil.parseDate(sdf, afterSelector, config);
      modifiedAfterTimestamp = Long.valueOf(dateAfter.getTime());
    }
    

    Date dateBefore = null;
    String beforeSelector = cli.getOption(OPT_CREATED_BEFORE, null);
    if (beforeSelector != null) {
      dateBefore = SubcommandUtil.parseDate(sdf, beforeSelector, config);
      modifiedBeforeTimestamp = Long.valueOf(dateBefore.getTime());
    }
    

    IScmCommandLineArgument changePathSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_CHANGE_PATH, null), config);
    SubcommandUtil.validateArgument(changePathSelector, RepoUtil.ItemType.VERSIONABLE);
    
    VersionableDTO versionable = null;
    if (changePathSelector != null) {
      IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
      String remotePath = changePathSelector.getItemSelector();
      String[] path = StringUtil.splitEscapedPath(remotePath);
      remotePath = StringUtil.createPathString(path);
      
      versionable = RepoUtil.getVersionableByPath(scmService, workspaceItemId, comp.getItemId().getUuidValue(), 
        remotePath, config);
      versionableItemId = versionable.getVersionable().getItemId().getUuidValue();
      versionableItemType = versionable.getVersionable().getItemType().getName();
      versionableItemTypeNamespace = versionable.getVersionable().getItemType().getNamespaceURI();
    }
    

    name = cli.getOption(OPT_CHANGE_NAME, null);
    

    if (cli.hasOption(OPT_CHANGE_TYPE)) {
      changeType = Integer.valueOf(getChangeType(cli.getOption(OPT_CHANGE_TYPE)));
    }
    return repo;
  }
  
  private void validateArguments(ICommandLine cli) throws FileSystemException {
    if (cli.hasOption(OPT_WORKITEMS))
    {
      if ((cli.hasOption(OPT_WORKSPACE)) || 
        (cli.hasOption(OPT_COMPONENT)) || 
        (cli.hasOption(OPT_BASELINE)) || 
        (cli.hasOption(OPT_SUSPENDEDBY)) || 
        (cli.hasOption(OPT_CREATOR)) || 
        (cli.hasOption(OPT_CREATED_AFTER)) || 
        (cli.hasOption(OPT_CREATED_BEFORE)) || 
        (cli.hasOption(OPT_CHANGE_PATH)) || 
        (cli.hasOption(OPT_CHANGE_NAME)) || 
        (cli.hasOption(OPT_CHANGE_TYPE)) || 
        (cli.hasOption(CommonOptions.OPT_MAXRESULTS))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ChangesetLocateCmd_WI_ALL_ONLY_FOR_WORKITEMS, 
          cli.getDefinition().getOption(OPT_WORKITEMS).getName(), 
          cli.getDefinition().getOption(OPT_WORKITEMS_ALL).getName()));
      }
    }
    
    if ((cli.hasOption(OPT_BASELINE)) && (!cli.hasOption(OPT_COMPONENT))) {
      throw StatusHelper.argSyntax(Messages.ListChangesetsCmd_MUST_SPECIFY_COMPONENT);
    }
    
    String changeType = cli.getOption(OPT_CHANGE_TYPE, null);
    if (changeType != null) {
      for (char c : changeType.toCharArray()) {
        Character item = Character.valueOf(c);
        
        if ((item.charValue() != 'a') && (item.charValue() != 'd') && (item.charValue() != 'c') && (item.charValue() != 'r') && (item.charValue() != 'm')) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.ListChangesetsCmd_INVALID_CHANGE_TYPE, changeType));
        }
      }
    }
    
    if ((cli.hasOption(OPT_CHANGE_PATH)) && ((!cli.hasOption(OPT_WORKSPACE)) || (!cli.hasOption(OPT_COMPONENT)))) {
      throw StatusHelper.argSyntax(Messages.ListChangesetsCmd_MUST_SPECIFY_WORKSPACE_COMPONENT);
    }
    
    if ((cli.hasOption(OPT_WORKITEMS_ALL)) && (!cli.hasOption(OPT_WORKITEMS))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ChangesetLocateCmd_WI_ALL_ONLY_FOR_WORKITEMS, 
        cli.getDefinition().getOption(OPT_WORKITEMS_ALL).getName(), 
        cli.getDefinition().getOption(OPT_WORKITEMS).getName()));
    }
  }
  
  private WorkspaceComponentDTO matchComponent(ParmsWorkspace ws, IScmCommandLineArgument compSelector, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector.getItemSelector());
    List<WorkspaceComponentDTO> matchedComps = new ArrayList();
    

    for (WorkspaceComponentDTO compDTO : wsDetails.getComponents()) {
      if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(compDTO.getItemId()))) || 
        (compSelector.getItemSelector().equals(compDTO.getName()))) {
        matchedComps.add(compDTO);
      }
    }
    

    if (matchedComps.size() == 0)
      throw StatusHelper.itemNotFound(NLS.bind(Messages.Common_COMP_NOT_FOUND, compSelector.getItemSelector()));
    if (matchedComps.size() > 1) {
      List<SubcommandUtil.ItemInfo> compsMatched = new ArrayList(matchedComps.size());
      for (WorkspaceComponentDTO comp : matchedComps) {
        compsMatched.add(new SubcommandUtil.ItemInfo(comp.getName(), comp.getItemId(), wsDetails.getRepositoryURL(), RepoUtil.ItemType.COMPONENT));
      }
      
      SubcommandUtil.displayAmbiguousSelectorException(compSelector.getItemSelector(), compsMatched, config);
      throw StatusHelper.ambiguousSelector(NLS.bind(Messages.Common_AMBIGUOUS_COMPONENT, compSelector.getItemSelector()));
    }
    
    return (WorkspaceComponentDTO)matchedComps.get(0);
  }
  
  private IComponent getComponent(IScmCommandLineArgument compSelector, ParmsWorkspace ws, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    IComponent component = null;
    
    if (ws != null)
    {
      WorkspaceComponentDTO comp = matchComponent(ws, compSelector, client, config);
      component = (IComponent)RepoUtil.getItem(IComponent.ITEM_TYPE, UUID.valueOf(comp.getItemId()), repo, config);
    }
    else {
      component = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    }
    
    return component;
  }
  
  public int getChangeType(String changeTypeSelector) {
    int changeType = 0;
    
    if (changeTypeSelector.indexOf('a') != -1) {
      changeType |= 0x1;
    }
    if (changeTypeSelector.indexOf('d') != -1) {
      changeType |= 0x10;
    }
    if (changeTypeSelector.indexOf('c') != -1) {
      changeType |= 0x2;
    }
    if (changeTypeSelector.indexOf('r') != -1) {
      changeType |= 0x4;
    }
    if (changeTypeSelector.indexOf('m') != -1) {
      changeType |= 0x8;
    }
    
    return changeType;
  }
}
