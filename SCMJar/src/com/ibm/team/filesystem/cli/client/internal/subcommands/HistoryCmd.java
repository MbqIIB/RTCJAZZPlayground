package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.AliasUtil.IAliasOptions;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.DeliveryInfo;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.ISymbolicLink;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IFolder;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmComponentHistory;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmContributor;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmContributorList;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmHistoryEntry;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmItemHistory;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetComponentHistory;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetContributors;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetItemHistory;
import java.io.PrintStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;

public class HistoryCmd extends AbstractSubcommand
{
  private static final int MAXIMUM_RESULT_SIZE = 1024;
  public HistoryCmd() {}
  
  class WsCompInfo
  {
    ParmsWorkspace workspace;
    String workspaceName;
    String componentId;
    String componentRootFolderId;
    String componentName;
    RepoUtil.ItemType wsType;
    
    public WsCompInfo(ParmsWorkspace ws, String wsName, String compId, String compRootFolderId, String compName, RepoUtil.ItemType type)
    {
      workspace = ws;
      workspaceName = wsName;
      componentId = compId;
      componentRootFolderId = compRootFolderId;
      componentName = compName;
      wsType = type;
    }
    
    public ParmsWorkspace getWorkspace() {
      return workspace;
    }
    
    public String getWorkspaceId() {
      return workspace.workspaceItemId;
    }
    
    public String getWorkspaceName() {
      return workspaceName;
    }
    
    public String getRepositoryUrl() {
      return workspace.repositoryUrl;
    }
    
    public String getComponentId() {
      return componentId;
    }
    
    public String getComponentRootFolderId() {
      return componentRootFolderId;
    }
    
    public String getComponentName() {
      return componentName;
    }
    
    public RepoUtil.ItemType getWsType() {
      return wsType;
    }
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    boolean isRemotePath = cli.hasOption(HistoryCmdOpts.OPT_REMOTE_PATH);
    boolean hasFile = cli.hasOption(HistoryCmdOpts.OPT_FILE);
    boolean hasComponent = (cli.hasOption(HistoryCmdOpts.OPT_COMPONENT)) || 
      (cli.hasOption(HistoryCmdOpts.OPT_COMPONENT_DEPRECATED));
    
    if ((isRemotePath) && ((!cli.hasOption(HistoryCmdOpts.OPT_WORKSPACE)) || 
      (!hasComponent) || (!hasFile))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.HistoryCmd_RemotePath_Missing_Args, HistoryCmdOpts.OPT_REMOTE_PATH.toString()));
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    

    ParmsWorkspace ws = null;
    if (cli.hasOption(HistoryCmdOpts.OPT_WORKSPACE)) {
      IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(HistoryCmdOpts.OPT_WORKSPACE), config);
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    

    WsCompInfo wsCompInfo = null;
    if ((cli.hasOption(HistoryCmdOpts.OPT_COMPONENT)) || 
      (cli.hasOption(HistoryCmdOpts.OPT_COMPONENT_DEPRECATED))) {
      IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(
        cli.hasOption(HistoryCmdOpts.OPT_COMPONENT) ? 
        cli.getOptionValue(HistoryCmdOpts.OPT_COMPONENT) : 
        cli.getOptionValue(HistoryCmdOpts.OPT_COMPONENT_DEPRECATED), 
        config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      
      if (repo != null) {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, Collections.singletonList(compSelector), repo, config);
      }
      
      wsCompInfo = getComponent(ws, compSelector.getItemSelector(), client, config, out);
      if (repo == null) {
        repo = RepoUtil.login(config, client, config.getConnectionInfo(wsCompInfo.getRepositoryUrl()));
        ws = wsCompInfo.getWorkspace();
      }
    }
    
    String versionableId = null;
    String versionableType = null;
    String versionableNameSpace = null;
    if (!isRemotePath) {
      if (hasFile) {
        String path = cli.getOption(HistoryCmdOpts.OPT_FILE);
        ResourcePropertiesDTO dto = RepoUtil.getResourceProperties(path, client, config);
        ShareDTO share = dto.getShare();
        String repositoryUrl = RepoUtil.getRepoUri(config, client, share);
        
        if ((wsCompInfo != null) && (!wsCompInfo.getComponentId().equals(share.getComponentItemId())) && 
          (!wsCompInfo.getWorkspaceId().equals(share.getContextItemId()))) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.HistoryCmd_PATH_NOT_IN_COMPONENT, 
            path, wsCompInfo.getComponentName()));
        }
        
        versionableId = dto.getItemId();
        if (dto.getVersionableItemType().equals("file")) {
          versionableType = IFileItem.ITEM_TYPE.getName();
          versionableNameSpace = IFileItem.ITEM_TYPE.getNamespaceURI();
        } else if (dto.getVersionableItemType().equals("folder")) {
          versionableType = IFolder.ITEM_TYPE.getName();
          versionableNameSpace = IFolder.ITEM_TYPE.getNamespaceURI();
        } else if (dto.getVersionableItemType().equals("symbolic_link")) {
          versionableType = ISymbolicLink.ITEM_TYPE.getName();
          versionableNameSpace = ISymbolicLink.ITEM_TYPE.getNamespaceURI();
        }
        
        if (wsCompInfo == null) {
          wsCompInfo = new WsCompInfo(new ParmsWorkspace(repositoryUrl, share.getContextItemId()), 
            share.getContextName(), share.getComponentItemId(), share.getRootVersionableItemId(), 
            share.getComponentName(), RepoUtil.ItemType.WORKSPACE);
        }
        
        if (ws == null) {
          ws = wsCompInfo.getWorkspace();
        } else if (!workspaceItemId.equals(wsCompInfo.getWorkspaceId())) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.HistoryCmd_PATH_NOT_IN_WORKSPACE, 
            path, wsCompInfo.getComponentName()));
        }
        
        repo = RepoUtil.getSharedRepository(repositoryUrl, true);
      } else if (wsCompInfo == null) {
        List<ShareDTO> shareList = new ArrayList();
        if (ws != null) {
          shareList = RepoUtil.getSharesInSandbox(workspaceItemId, client, config);
        }
        else {
          String repoUri = cli.getOption(CommonOptions.OPT_URI, null);
          if (repoUri != null) {
            repo = RepoUtil.login(config, client, config.getConnectionInfo(repoUri));
          }
          ParmsWorkspace wsFound = RepoUtil.findWorkspaceInSandbox(null, 
            repo != null ? repo.getId() : null, client, config);
          shareList = RepoUtil.getSharesInSandbox(workspaceItemId, client, config);
        }
        if (shareList.size() != 1) {
          throw StatusHelper.ambiguousSelector(Messages.Common_UNIQUE_LOADED_COMP);
        }
        ShareDTO share = (ShareDTO)shareList.get(0);
        String repositoryUrl = RepoUtil.getRepoUri(config, client, share);
        wsCompInfo = new WsCompInfo(new ParmsWorkspace(repositoryUrl, share.getContextItemId()), 
          share.getContextName(), share.getComponentItemId(), share.getRootVersionableItemId(), 
          share.getComponentName(), RepoUtil.ItemType.WORKSPACE);
        
        if (ws == null) {
          ws = new ParmsWorkspace(repositoryUrl, share.getContextItemId());
        } else if (!workspaceItemId.equals(wsCompInfo.getWorkspaceId())) {
          throw StatusHelper.ambiguousSelector(Messages.Common_UNIQUE_LOADED_COMP);
        }
        
        repo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
      }
    } else {
      IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
      String remotePath = cli.getOption(HistoryCmdOpts.OPT_FILE);
      String[] path = StringUtil.splitEscapedPath(remotePath);
      remotePath = StringUtil.createPathString(path);
      
      VersionableDTO ver = RepoUtil.getVersionableByPath(scmService, wsCompInfo.getWorkspaceId(), wsCompInfo.getComponentId(), remotePath, config);
      
      versionableId = ver.getVersionable().getItemId().getUuidValue();
      versionableType = ver.getVersionable().getItemType().getName();
      versionableNameSpace = ver.getVersionable().getItemType().getNamespaceURI();
    }
    

    int maxResults = RepoUtil.getMaxResultsOption(cli);
    


    if (maxResults == Integer.MAX_VALUE) {
      maxResults = 1024;
    }
    if (maxResults > 1024) {
      throw StatusHelper.invalidProperty(NLS.bind(Messages.HistoryCmd_0, Integer.valueOf(1024)));
    }
    

    boolean verbose = (cli.hasOption(CommonOptions.OPT_VERBOSE)) || (config.isJSONEnabled());
    
    IScmRichClientRestService scmRichService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
    
    int requestSize = maxResults + 1;
    List<ScmHistoryEntry> entries; List<ScmHistoryEntry> entries; if ((hasFile) || (versionableId != null)) {
      entries = getHistoryEntriesForVersionable(scmRichService, ws, 
        wsCompInfo.getComponentId(), versionableId, versionableType, 
        versionableNameSpace, requestSize, config);
    } else {
      entries = getHistoryEntriesForComponent(scmRichService, ws, 
        wsCompInfo.getComponentId(), requestSize, config);
    }
    
    List<String> csIds = new ArrayList();
    Map<String, PendingChangesUtil.DeliveryInfo> deliveryInfo = hasFile ? null : new HashMap();
    
    Set<String> contributorIds = new HashSet();
    boolean hasMore = false;
    String id; for (ScmHistoryEntry entry : entries) {
      if (csIds.size() == maxResults)
      {

        hasMore = true;
        break;
      }
      id = entry.getChangeSetItemId();
      csIds.add(id);
      

      if (deliveryInfo != null) {
        String contributorId = entry.getAddedById();
        contributorIds.add(contributorId);
        deliveryInfo.put(id, new PendingChangesUtil.DeliveryInfo(id, contributorId, formatDate(config, entry.getDateAdded().getTime())));
      }
    }
    


    if (deliveryInfo != null) {
      try {
        Map<String, String> contributors = fetchContributors(scmRichService, contributorIds);
        for (PendingChangesUtil.DeliveryInfo info : deliveryInfo.values()) {
          String name = (String)contributors.get(info.getDeliveredBy());
          if (name != null) {
            info.setDeliveredBy(name);
          }
        }
      }
      catch (TeamRepositoryException e) {
        String message = NLS.bind(Messages.ErrorFetchingUser, e.getMessage());
        StatusHelper.logException(message, e);
      }
    }
    

    PendingChangesUtil.PendingChangesOptions pcOptions = new PendingChangesUtil.PendingChangesOptions();
    pcOptions.enablePrinter(4);
    if (!hasFile) {
      pcOptions.enablePrinter(34);
    }
    if (verbose) {
      pcOptions.setVerbose(true);
      pcOptions.enablePrinter(6);
      pcOptions.enablePrinter(7);
      pcOptions.enablePrinter(31);
      pcOptions.enablePrinter(21);
      
      if ((config.getAliasConfig().showUuid()) || (config.isJSONEnabled())) {
        pcOptions.enablePrinter(14);
        pcOptions.enablePrinter(26);
      }
      
      if (config.isJSONEnabled()) {
        pcOptions.enablePrinter(22);
        pcOptions.enablePrinter(19);
      }
    }
    
    String workspaceId = null;
    if (ws != null) {
      workspaceId = workspaceItemId;
    }
    ChangeSetStateFactory stateFactory = ChangeSetStateFactory.createChangeSetstateFactory(client, repo, workspaceId, wsCompInfo.getComponentId());
    com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.printChangeSets2(repo, csIds, stateFactory, pcOptions, out, client, config, deliveryInfo);
    
    if ((hasMore) && (!config.isJSONEnabled())) {
      config.getContext().stdout().println(Messages.HistoryCmd_10);
    }
  }
  


  private List<ScmHistoryEntry> getHistoryEntriesForComponent(IScmRichClientRestService scmRichService, ParmsWorkspace ws, String componentId, int maxResults, IScmClientConfiguration config)
    throws CLIFileSystemClientException, FileSystemException
  {
    IScmRichClientRestService.ParmsGetComponentHistory parms = new IScmRichClientRestService.ParmsGetComponentHistory();
    contextItemType = IWorkspace.ITEM_TYPE.getName();
    contextItemNamespace = IWorkspace.ITEM_TYPE.getNamespaceURI();
    contextItemId = workspaceItemId;
    componentItemId = componentId;
    desiredPageSize = maxResults;
    try
    {
      ScmComponentHistory result = scmRichService.getComponentHistory(parms);
      return result.getHistoryEntries();
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.HistoryCmd_25, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
  }
  
  private String formatDate(IScmClientConfiguration config, long time) {
    SimpleDateFormat sdf = SubcommandUtil.getDateFormat("dd-MMM-yyyy hh:mm a", config);
    return sdf.format(new Date(time));
  }
  




  private List<ScmHistoryEntry> getHistoryEntriesForVersionable(IScmRichClientRestService scmService, ParmsWorkspace ws, String componentId, String versionableId, String versionableType, String versionableNameSpace, int maxResults, IScmClientConfiguration config)
    throws CLIFileSystemClientException, FileSystemException
  {
    IScmRichClientRestService.ParmsGetItemHistory parms = new IScmRichClientRestService.ParmsGetItemHistory();
    componentItemId = componentId;
    desiredPageSize = maxResults;
    versionableItemId = versionableId;
    versionableItemNamespace = versionableNameSpace;
    versionableItemType = versionableType;
    contextItemType = IWorkspace.ITEM_TYPE.getName();
    contextItemId = workspaceItemId;
    contextItemNamespace = IWorkspace.ITEM_TYPE.getNamespaceURI();
    requireMergeGraph = true;
    try
    {
      ScmItemHistory result = scmService.getItemHistory(parms);
      return result.getHistoryEntries();
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.HistoryCmd_25, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
  }
  


  String[] nextChunk(Iterator<String> iterator, int chunkSize)
  {
    List<String> result = new ArrayList();
    for (int i = 0; (iterator.hasNext()) && (i < chunkSize); i++) {
      result.add((String)iterator.next());
    }
    return (String[])result.toArray(new String[result.size()]);
  }
  


  Map<String, String> fetchContributors(IScmRichClientRestService scmService, Set<String> itemIds)
    throws TeamRepositoryException
  {
    Map<String, String> result = new HashMap();
    String[] toFetch;
    Iterator<String> iterator;
    String[] toFetch;
    if (itemIds.size() < 512) {
      Iterator<String> iterator = Collections.EMPTY_SET.iterator();
      toFetch = (String[])itemIds.toArray(new String[itemIds.size()]);
    } else {
      iterator = itemIds.iterator();
      toFetch = nextChunk(iterator, 512);
    }
    while (toFetch.length > 0) {
      IScmRichClientRestService.ParmsGetContributors cParms = new IScmRichClientRestService.ParmsGetContributors();
      contributorItemIds = toFetch;
      ScmContributorList contributors = scmService.getContributors(cParms);
      List<ScmContributor> all = contributors.getContributors();
      for (ScmContributor contributor : all) {
        result.put(contributor.getItemId(), contributor.getName());
      }
      toFetch = nextChunk(iterator, 512);
    }
    return result;
  }
  
  private WsCompInfo getComponent(ParmsWorkspace ws, String compSelector, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out) throws FileSystemException
  {
    List<WsCompInfo> wsCompList = new ArrayList();
    IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector);
    ShareDTO share; WorkspaceComponentDTO wsComp; if (ws == null) {
      List<ShareDTO> shareList = RepoUtil.getSharesInSandbox(null, client, config);
      List<String> compsFound = new ArrayList();
      
      for (Iterator localIterator = shareList.iterator(); localIterator.hasNext();) { share = (ShareDTO)localIterator.next();
        if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(share.getComponentItemId()))) || (
          (compSelector.equals(share.getComponentName())) && 
          (!compsFound.contains(share.getComponentItemId())))) {
          wsCompList.add(new WsCompInfo(new ParmsWorkspace(RepoUtil.getRepoUri(
            config, client, share), share.getContextItemId()), 
            share.getContextName(), share.getComponentItemId(), share.getRootVersionableItemId(), 
            share.getComponentName(), RepoUtil.ItemType.WORKSPACE));
          compsFound.add(share.getComponentItemId());
        }
      }
    }
    else {
      WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
      
      for (share = wsDetails.getComponents().iterator(); share.hasNext();) { wsComp = (WorkspaceComponentDTO)share.next();
        if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(wsComp.getItemId()))) || 
          (compSelector.equals(wsComp.getName()))) {
          wsCompList.add(new WsCompInfo(new ParmsWorkspace(wsDetails.getRepositoryURL(), wsDetails.getItemId()), 
            wsDetails.getName(), wsComp.getItemId(), wsComp.getRootFolder(), wsComp.getName(), 
            wsDetails.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE));
        }
      }
    }
    
    if (wsCompList.size() == 0) {
      String message = NLS.bind(Messages.Common_COMP_NOT_FOUND, compSelector);
      if (ws == null) {
        message = NLS.bind(Messages.Common_LOADED_COMP_NOT_FOUND, compSelector);
      }
      throw StatusHelper.itemNotFound(message); }
    if (wsCompList.size() > 1) {
      out.println(Messages.HistroyCmd_COMPONENT_MATCHED_MULTI_WORKSPACE);
      for (WsCompInfo wsComp : wsCompList) {
        out.indent().println(AliasUtil.selector(wsComp.getWorkspaceName(), 
          UUID.valueOf(wsComp.getWorkspaceId()), wsComp.getRepositoryUrl(), wsComp.getWsType()));
      }
      throw StatusHelper.ambiguousSelector(NLS.bind(Messages.Common_AMBIGUOUS_COMPONENT, compSelector));
    }
    
    return (WsCompInfo)wsCompList.get(0);
  }
}
