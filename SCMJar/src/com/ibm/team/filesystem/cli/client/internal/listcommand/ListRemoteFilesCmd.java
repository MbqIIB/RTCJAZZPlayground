package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.AliasUtil.IAliasOptions;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.IItemType.IRegistry;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IFolder;
import com.ibm.team.scm.common.IFolderHandle;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmComponent2;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmComponent2List;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmFolderEntryReport;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmFolderEntryReportList;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmHandle;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionableIdentifier;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionableIdentifierList;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionablePath;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionablePermissionsReport;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionablePermissionsResult;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetComponents;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetVersionableChildren;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetVersionableIdentifiers;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetVersionablePermissions;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import org.eclipse.osgi.util.NLS;






public class ListRemoteFilesCmd
  extends AbstractSubcommand
{
  static final int DEPTH_INFINITE = -1;
  private static final String READ_ACCESS_TYPE = "type";
  private static final String READ_ACCESS_NAME = "name";
  private static final String READ_ACCESS_UUID = "uuid";
  public ListRemoteFilesCmd() {}
  
  static enum Mode
  {
    WORKSPACE,  SNAPSHOT,  BASELINE; }
  static enum VersionMode { HIDE,  SHORT_VERSION,  LONG_VERSION;
  }
  




  private Map<String, String> readContextCache = new HashMap();
  

  private static final int MAX_BATCH_SIZE = 50;
  
  private static final Comparator<String> PATH_COMPARATOR = new Comparator()
  {
    public int compare(String object1, String object2) {
      int l1 = object1.length();
      int l2 = object2.length();
      int end = l1 < l2 ? l1 : l2;
      for (int i = 0; i < end; i++) {
        char c1 = object1.charAt(i);
        char c2 = object2.charAt(i);
        if ((c1 == '/') && (c2 != '/'))
          return -1;
        if ((c2 == '/') && (c1 != '/')) {
          return 1;
        }
        int compare = c1 - c2;
        if (compare != 0) {
          return compare;
        }
      }
      return l1 - l2;
    }
  };
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    


    int depth = -1;
    
    String depthString = cli.getOption(ListRemoteFilesOptions.OPT_DEPTH, null);
    if (depthString != null) {
      if (!"-".equals(depthString)) {
        try {
          depth = Integer.parseInt(depthString);
        } catch (NumberFormatException localNumberFormatException) {
          throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_5, depthString));
        }
      }
    } else {
      depth = 1;
    }
    

    VersionMode vMode = VersionMode.HIDE;
    
    if ((cli.hasOption(ListRemoteFilesOptions.OPT_SHOW_SHORT_VERSION_ID)) && 
      (cli.hasOption(ListRemoteFilesOptions.OPT_SHOW_FULL_VERSION_ID)))
    {
      throw StatusHelper.argSyntax(NLS.bind(
        com.ibm.team.filesystem.cli.client.internal.Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        cli.getDefinition().getOption(ListRemoteFilesOptions.OPT_SHOW_SHORT_VERSION_ID).getName(), 
        cli.getDefinition().getOption(ListRemoteFilesOptions.OPT_SHOW_FULL_VERSION_ID).getName()));
    }
    
    if (cli.hasOption(ListRemoteFilesOptions.OPT_SHOW_SHORT_VERSION_ID)) {
      vMode = VersionMode.SHORT_VERSION;
    } else if (cli.hasOption(ListRemoteFilesOptions.OPT_SHOW_FULL_VERSION_ID)) {
      vMode = VersionMode.LONG_VERSION;
    }
    

    boolean showAccess = config.getSubcommandCommandLine().hasOption(ListRemoteFilesOptions.OPT_SHOW_ACCESS);
    

    Mode mode = Mode.WORKSPACE;
    
    int modeCount = 0;
    if (cli.hasOption(ListRemoteFilesOptions.OPT_WORKSPACE)) {
      mode = Mode.WORKSPACE;
      modeCount++;
    }
    
    if (cli.hasOption(ListRemoteFilesOptions.OPT_SNAPSHOT)) {
      mode = Mode.SNAPSHOT;
      modeCount++;
    }
    
    if (cli.hasOption(ListRemoteFilesOptions.OPT_BASELINE)) {
      mode = Mode.BASELINE;
      modeCount++;
    }
    
    if (modeCount > 1) {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.Common_SPECIFY_1_OF_3_ARGUMENTS, new String[] {
        ListRemoteFilesOptions.OPT_WORKSPACE.getName(), ListRemoteFilesOptions.OPT_SNAPSHOT.getName(), ListRemoteFilesOptions.OPT_BASELINE.getName() }));
    }
    

    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    IScmCommandLineArgument selector = ScmCommandLineArgument.create(cli.getOptionValue(ListRemoteFilesOptions.OPT_SELECTOR), config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, selector);
    

    IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ListRemoteFilesOptions.OPT_COMPONENT_SELECTOR), config);
    RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, Collections.singletonList(compSelector), repo, config);
    SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    


    IItemHandle contextItem = null;
    
    String compId;
    
    String compId;
    
    if (mode == Mode.WORKSPACE) {
      SubcommandUtil.validateArgument(selector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      IWorkspace wsFound = RepoUtil.getWorkspace(selector.getItemSelector(), true, true, repo, config);
      ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
      
      contextItem = wsFound.getItemHandle();
      

      WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
      
      ArrayList<WorkspaceComponentDTO> matches = new ArrayList();
      for (WorkspaceComponentDTO compDTO : wsDetails.getComponents()) {
        if (matches(compSelector, compDTO)) {
          matches.add(compDTO);
        }
      }
      

      if (matches.isEmpty()) {
        throw StatusHelper.itemNotFound(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.Common_COMP_NOT_FOUND, compSelector.getItemSelector()));
      }
      
      if (matches.size() > 1) {
        config.getContext().stderr().println(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_MULTI_MATCH);
        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr()).indent();
        
        showAmbiguousWorkspaceComponents(matches, repo, err);
        
        throw StatusHelper.ambiguousSelector(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_SPECIFY_SINGLE);
      }
      
      compId = ((WorkspaceComponentDTO)matches.get(0)).getItemId();
    } else { String compId;
      if (mode == Mode.SNAPSHOT) {
        SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.SNAPSHOT);
        IBaselineSet snapshot = RepoUtil.getSnapshot(null, selector.getItemSelector(), repo, config);
        contextItem = snapshot.getItemHandle();
        

        List<IBaseline> bls = RepoUtil.getItems(IBaseline.ITEM_TYPE, snapshot.getBaselines(), repo, config);
        ArrayList<IComponentHandle> compHandles = new ArrayList(bls.size());
        
        for (IBaseline blHandle : bls) {
          compHandles.add(blHandle.getComponent());
        }
        
        List<IComponent> comps = RepoUtil.getItems(IComponent.ITEM_TYPE, compHandles, repo, config);
        
        Object matches = new ArrayList(comps.size());
        for (IComponent comp : comps) {
          if (matches(compSelector, comp)) {
            ((ArrayList)matches).add(comp);
          }
        }
        

        if (((ArrayList)matches).isEmpty()) {
          throw StatusHelper.itemNotFound(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.Common_COMP_NOT_FOUND, compSelector.getItemSelector()));
        }
        
        if (((ArrayList)matches).size() > 1) {
          config.getContext().stderr().println(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_MULTI_MATCH);
          IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr()).indent();
          
          showAmbiguousIComponents((ArrayList)matches, repo, err);
          
          throw StatusHelper.ambiguousSelector(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_SPECIFY_SINGLE);
        }
        
        compId = ((IComponent)((ArrayList)matches).get(0)).getItemId().getUuidValue();
      } else {
        IComponent comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
        
        SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.BASELINE);
        IBaseline bl = RepoUtil.getBaseline(selector.getItemSelector(), comp.getItemId().getUuidValue(), null, repo, client, config);
        contextItem = bl.getItemHandle();
        
        compId = bl.getComponent().getItemId().getUuidValue();
      }
    }
    
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
      .getServiceInterface(IScmRichClientRestService.class);
    
    String remotePath = null;
    if (cli.hasOption(ListRemoteFilesOptions.OPT_REMOTE_PATH)) {
      remotePath = cli.getOption(ListRemoteFilesOptions.OPT_REMOTE_PATH);
      String[] path = StringUtil.splitEscapedPath(remotePath);
      remotePath = path.length == 0 ? Character.toString('/') : toPath(path, false);
    } else {
      remotePath = Character.toString('/');
    }
    
    ScmVersionablePath scmPath = RepoUtil.getVersionable2(scmService, contextItem, compId, remotePath, config);
    if ((scmPath == null) || (scmPath.getVersionable() == null)) {
      throw StatusHelper.itemNotFound(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_3, remotePath));
    }
    
    String[] pathSegs = (String[])scmPath.getSegments().toArray(new String[scmPath.getSegments().size()]);
    String path = toPath(pathSegs, scmPath.getVersionable().getItemType().equals(IFolder.ITEM_TYPE));
    
    JSONObject jPath = new JSONObject();
    jPath.put("path", path);
    jPath.put("uuid", scmPath.getVersionable().getItemId().getUuidValue());
    jPath.put("state-id", scmPath.getVersionable().getStateId().getUuidValue());
    if ((!VersionMode.HIDE.equals(vMode)) && (!scmPath.getVersionable().getItemType().equals(IFolder.ITEM_TYPE))) {
      String stateId = scmPath.getVersionable().getStateId().getUuidValue();
      Map<String, ScmVersionableIdentifier> identifiers = fetchVersionIdentifiers(scmService, Collections.singletonList(stateId), repo, config);
      if (identifiers.containsKey(stateId)) {
        jPath.put("version", JSONPrintUtil.jsonizeVersionId((ScmVersionableIdentifier)identifiers.get(stateId)));
      }
    }
    if (showAccess) {
      Object versionablePermissions = fetchVersionablePermissions(scmService, compId, Collections.singletonList(scmPath.getVersionable()), repo, config);
      JSONObject permissions = (JSONObject)((Map)versionablePermissions).get(scmPath.getVersionable().getItemId().getUuidValue());
      if (permissions != null) {
        jPath.put("jazz.read-access", permissions);
      }
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    if ((!scmPath.getVersionable().getItemType().equals(IFolder.ITEM_TYPE)) || (depth == 0)) {
      printFiles(Collections.singletonList(jPath), out, repo.getRepositoryURI(), config, vMode);
      return;
    }
    

    jsonizeFiles(path, jPath, contextItem, compId, scmPath.getVersionable(), depth, repo, config, vMode);
  }
  
  private void showAmbiguousIComponents(ArrayList<IComponent> matches, ITeamRepository repo, IndentingPrintStream err) throws CLIFileSystemClientException, FileSystemException
  {
    IScmRichClientRestService.ParmsGetComponents parmsGetComps = new IScmRichClientRestService.ParmsGetComponents();
    componentItemIds = new String[matches.size()];
    
    for (int i = 0; i < componentItemIds.length; i++) {
      componentItemIds[i] = ((IComponent)matches.get(i)).getItemId().getUuidValue();
    }
    
    showAmbiguousComponents(parmsGetComps, repo, err);
  }
  
  private void showAmbiguousWorkspaceComponents(ArrayList<WorkspaceComponentDTO> matches, ITeamRepository repo, IndentingPrintStream err)
    throws CLIFileSystemClientException, FileSystemException
  {
    IScmRichClientRestService.ParmsGetComponents parmsGetComps = new IScmRichClientRestService.ParmsGetComponents();
    componentItemIds = new String[matches.size()];
    
    for (int i = 0; i < componentItemIds.length; i++) {
      componentItemIds[i] = ((WorkspaceComponentDTO)matches.get(i)).getItemId();
    }
    
    showAmbiguousComponents(parmsGetComps, repo, err);
  }
  
  public void showAmbiguousComponents(IScmRichClientRestService.ParmsGetComponents parmsGetComps, ITeamRepository repo, IndentingPrintStream out)
    throws CLIFileSystemClientException, FileSystemException
  {
    ScmComponent2List componentList = null;
    try {
      IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
      componentList = scmService.getComponents2(parmsGetComps);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap("Failed to fetch components", e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    for (ScmComponent2 compInfo : componentList.getComponents()) {
      String ownerName = RepoUtil.getOwnerName(compInfo.getOwner().getItemId(), compInfo.getOwner().getItemType(), repo, config);
      
      out.println(NLS.bind("{0} owned by {1}", 
        AliasUtil.selector(compInfo.getName(), UUID.valueOf(compInfo.getItemId()), repo.publicUriRoot(), RepoUtil.ItemType.COMPONENT), 
        ownerName));
    }
  }
  

  private boolean matches(IScmCommandLineArgument sel, IComponent comp)
  {
    IUuidAliasRegistry.IUuidAlias alias = sel.getAlias();
    if (alias == null) {
      return sel.getValuePrefix().equals(comp.getName());
    }
    
    return alias.getUuid().equals(comp.getItemId());
  }
  
  private boolean matches(IScmCommandLineArgument sel, WorkspaceComponentDTO dto) {
    IUuidAliasRegistry.IUuidAlias alias = sel.getAlias();
    if (alias == null) {
      return sel.getValuePrefix().equals(dto.getName());
    }
    
    return alias.getUuid().getUuidValue().equals(dto.getItemId());
  }
  
  private String getPrintableAccessType(String internalAccessType) {
    if (internalAccessType == null) {
      return com.ibm.team.filesystem.cli.client.internal.Messages.Common_UNKNOWN;
    }
    
    if (internalAccessType.equals(IAccessGroup.ITEM_TYPE.getName()))
      return com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_ACCESS_GROUP;
    if (internalAccessType.equals(IProjectArea.ITEM_TYPE.getName()))
      return com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_PROJECT_AREA;
    if (internalAccessType.equals(ITeamArea.ITEM_TYPE.getName()))
      return com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_TEAM_AREA;
    if (internalAccessType.equals(IComponent.ITEM_TYPE.getName()))
      return com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_COMPONENT_SCOPED;
    if (internalAccessType.equals(IContributor.ITEM_TYPE.getName())) {
      return com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_CONTRIBUTOR;
    }
    return internalAccessType;
  }
  

  private void printFiles(Collection paths, IndentingPrintStream out, String repoUri, IScmClientConfiguration config, VersionMode vMode)
    throws FileSystemException
  {
    boolean showVersionId = (vMode == VersionMode.LONG_VERSION) || (vMode == VersionMode.SHORT_VERSION);
    JSONArray array; if (config.isJSONEnabled()) {
      JSONObject jResult = new JSONObject();
      array = new JSONArray();
      array.addAll(paths);
      jResult.put("remote-files", array);
      config.getContext().stdout().print(jResult);
      return;
    }
    
    for (Object obj : paths) {
      JSONObject jPath = (JSONObject)obj;
      String versionId = null;
      JSONObject version = (JSONObject)jPath.get("version");
      if (version != null) {
        switch (vMode) {
        case SHORT_VERSION: 
          versionId = JSONPrintUtil.jsonizeGetLongVersionId(version);
          break;
        case LONG_VERSION: 
          versionId = JSONPrintUtil.jsonizeGetShortVersionId(version);
        }
        
      }
      
      JSONObject access = (JSONObject)jPath.get("jazz.read-access");
      String output = null;
      if (config.getAliasConfig().showUuid()) {
        if ((showVersionId) && (versionId != null)) {
          output = NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_PRINT_UUID_VERSION_ID, new String[] { (String)jPath.get("uuid"), (String)jPath.get("state-id"), (String)jPath.get("path"), versionId });
        } else {
          output = NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_PRINT, new String[] { (String)jPath.get("uuid"), (String)jPath.get("state-id"), (String)jPath.get("path") });
        }
      } else if ((showVersionId) && (versionId != null)) {
        output = NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_PRINT_VERSION_ID, new String[] { (String)jPath.get("path"), versionId });
      } else {
        output = (String)jPath.get("path");
      }
      
      if (access != null) {
        String printableAccessType = getPrintableAccessType((String)access.get("type"));
        output = output + ' ';
        if (access.get("name") != null) {
          if ((config.getAliasConfig().showUuid()) && (access.get("uuid") != null)) {
            output = output + NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_PRINT_ACCESS_UUID, new Object[] { printableAccessType, access.get("uuid"), access.get("name") });
          } else {
            output = output + NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_PRINT_ACCESS, printableAccessType, access.get("name"));
          }
        }
      }
      
      out.println(output);
    }
  }
  
  private IScmRichClientRestService.ParmsGetVersionableChildren createVersionableChildrenParms(IItemHandle contextItem, String componentId, List<Map.Entry<String, IVersionableHandle>> batch) {
    IScmRichClientRestService.ParmsGetVersionableChildren parmsVerChild = new IScmRichClientRestService.ParmsGetVersionableChildren();
    contextItemId = contextItem.getItemId().getUuidValue();
    contextItemType = contextItem.getItemType().getName();
    contextItemNamespace = contextItem.getItemType().getNamespaceURI();
    componentItemId = componentId;
    versionableItemIds = new String[batch.size()];
    versionableItemTypes = new String[batch.size()];
    versionableItemNamespaces = new String[batch.size()];
    
    int idx = 0;
    for (Map.Entry<String, IVersionableHandle> entry : batch) {
      versionableItemIds[idx] = ((IVersionableHandle)entry.getValue()).getItemId().getUuidValue();
      versionableItemTypes[idx] = ((IVersionableHandle)entry.getValue()).getItemType().getName();
      versionableItemNamespaces[idx] = ((IVersionableHandle)entry.getValue()).getItemType().getNamespaceURI();
      idx++;
    }
    return parmsVerChild;
  }
  
  private int calculateDepth(String path) {
    int depth = -1;
    for (int i = 0; i < path.length(); i++) {
      if (path.charAt(i) == '/')
        depth++;
    }
    return depth;
  }
  
  private void jsonizeFiles(String path, JSONObject root, IItemHandle contextItem, String compId, IVersionableHandle versionable, int maxDepth, ITeamRepository repo, IScmClientConfiguration config, VersionMode vMode) throws FileSystemException
  {
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    boolean showAccess = config.getSubcommandCommandLine().hasOption(ListRemoteFilesOptions.OPT_SHOW_ACCESS);
    
    int initialDepth = calculateDepth(path);
    
    TreeMap<String, IVersionableHandle> pathQueue = new TreeMap(PATH_COMPARATOR);
    TreeMap<String, JSONObject> results = new TreeMap(PATH_COMPARATOR);
    results.put(path, root);
    pathQueue.put(path, versionable);
    Iterator localIterator1;
    for (; !pathQueue.isEmpty(); 
        




































        localIterator1.hasNext())
    {
      if (!config.isJSONEnabled())
      {
        SortedMap<String, JSONObject> toPrint = results.headMap((String)pathQueue.firstKey());
        if (!toPrint.isEmpty()) {
          printFiles(toPrint.values(), out, repo.getRepositoryURI(), config, vMode);
          
          toPrint.clear();
        }
      }
      
      int batchSize = pathQueue.size() > 50 ? 50 : pathQueue.size();
      
      List<Map.Entry<String, IVersionableHandle>> batch = new ArrayList(batchSize);
      while (batch.size() < batchSize) {
        batch.add(pathQueue.pollFirstEntry());
      }
      
      ScmFolderEntryReportList folderReportList = null;
      try {
        IScmRichClientRestService.ParmsGetVersionableChildren parms = createVersionableChildrenParms(contextItem, compId, batch);
        folderReportList = scmService.getVersionableChildren(parms);
      } catch (TeamRepositoryException localTeamRepositoryException) {
        throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListRemoteFilesCmd_FAILED_TO_GET_VER_CHILDREN, path));
      }
      
      Map<String, JSONObject> versionablePermissions = Collections.emptyMap();
      if (showAccess) {
        versionablePermissions = fetchVersionablePermissions(scmService, compId, folderReportList, repo, config);
      }
      
      Map<String, ScmVersionableIdentifier> versionIds = Collections.emptyMap();
      if (!VersionMode.HIDE.equals(vMode)) {
        versionIds = fetchVersionIdentifiers(scmService, folderReportList, repo, config);
      }
      
      int batchIdx = 0;
      localIterator1 = folderReportList.getFolderEntryReports().iterator(); continue;ScmFolderEntryReport childReport = (ScmFolderEntryReport)localIterator1.next();
      Map.Entry<String, IVersionableHandle> parentEntry = (Map.Entry)batch.get(batchIdx++);
      
      for (Map.Entry<String, IVersionable> entry : childReport.getEntries().entrySet()) {
        IVersionableHandle verHandle = (IVersionableHandle)entry.getValue();
        
        String childRemotePath = appendPathSegment((String)parentEntry.getKey(), (String)entry.getKey(), verHandle instanceof IFolderHandle);
        
        JSONObject jPath = new JSONObject();
        results.put(childRemotePath, jPath);
        jPath.put("path", childRemotePath);
        jPath.put("uuid", verHandle.getItemId().getUuidValue());
        jPath.put("state-id", verHandle.getStateId().getUuidValue());
        
        JSONObject permissions = (JSONObject)versionablePermissions.get(verHandle.getItemId().getUuidValue());
        if (permissions != null) {
          jPath.put("jazz.read-access", permissions);
        }
        
        ScmVersionableIdentifier vid = (ScmVersionableIdentifier)versionIds.get(verHandle.getStateId().getUuidValue());
        if (vid != null) {
          jPath.put("version", JSONPrintUtil.jsonizeVersionId(vid));
        }
        

        int currDepth = calculateDepth(childRemotePath) - initialDepth;
        if (((verHandle instanceof IFolderHandle)) && ((maxDepth == -1) || (currDepth < maxDepth))) {
          pathQueue.put(childRemotePath, verHandle);
        }
      }
    }
    

    JSONArray jArray = null;
    if (config.isJSONEnabled()) {
      jArray = new JSONArray(results.size());
      jArray.addAll(results.values());
    }
    
    printFiles(jArray != null ? jArray : results.values(), out, repo.getRepositoryURI(), config, vMode);
  }
  
  private Map<String, ScmVersionableIdentifier> fetchVersionIdentifiers(IScmRichClientRestService scmService, ScmFolderEntryReportList folderReport, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException {
    List<String> versionableStates = new ArrayList(2048);
    Iterator localIterator2; for (Iterator localIterator1 = folderReport.getFolderEntryReports().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      ScmFolderEntryReport folderEntry = (ScmFolderEntryReport)localIterator1.next();
      localIterator2 = folderEntry.getEntries().values().iterator(); continue;IVersionableHandle versionable = (IVersionableHandle)localIterator2.next();
      if (!(versionable instanceof IFolderHandle)) {
        versionableStates.add(versionable.getStateId().getUuidValue());
      }
    }
    
    return fetchVersionIdentifiers(scmService, versionableStates, repo, config);
  }
  
  private Map<String, ScmVersionableIdentifier> fetchVersionIdentifiers(IScmRichClientRestService scmService, List<String> versionableStates, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException {
    IScmRichClientRestService.ParmsGetVersionableIdentifiers parms = new IScmRichClientRestService.ParmsGetVersionableIdentifiers();
    Map<String, ScmVersionableIdentifier> results = new HashMap();
    int batchStart = 0;
    
    int batchSize = versionableStates.size() > 2048 ? 2048 : versionableStates.size();
    while (batchStart < versionableStates.size()) {
      versionableStateIds = ((String[])versionableStates.subList(batchStart, batchStart + batchSize).toArray(new String[batchSize]));
      
      ScmVersionableIdentifierList versionIdentifiers = null;
      try {
        versionIdentifiers = scmService.postGetVersionableIdentifiers(parms);
        for (ScmVersionableIdentifier identifier : versionIdentifiers.getVersionableIdentifiers()) {
          results.put(identifier.getStateId(), identifier);
        }
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(com.ibm.team.filesystem.cli.core.internal.Messages.RepoUtil_CANNOT_DETERMINE_VERSION_IDS, e, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      batchStart += batchSize;
      batchSize = batchStart + 2048 > versionableStates.size() ? versionableStates.size() - batchStart : 2048;
    }
    return results;
  }
  
  private Map<String, JSONObject> fetchVersionablePermissions(IScmRichClientRestService scmService, String componentId, ScmFolderEntryReportList folderReport, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    List<IVersionableHandle> versionables = new ArrayList(2048);
    for (ScmFolderEntryReport folderEntry : folderReport.getFolderEntryReports()) {
      Map<String, IVersionable> entries = folderEntry.getEntries();
      versionables.addAll(entries.values());
    }
    return fetchVersionablePermissions(scmService, componentId, versionables, repo, config);
  }
  
  private Map<String, JSONObject> fetchVersionablePermissions(IScmRichClientRestService scmService, String componentId, List<IVersionableHandle> versionables, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    IScmRichClientRestService.ParmsGetVersionablePermissions parms = new IScmRichClientRestService.ParmsGetVersionablePermissions();
    componentId = componentId;
    
    Map<String, JSONObject> results = new HashMap();
    int batchStart = 0;
    int batchSize = versionables.size() > 50 ? 50 : versionables.size();
    while (batchStart < versionables.size()) {
      versionableItemId = new String[batchSize];
      versionableItemType = new String[batchSize];
      versionableItemTypeNamespace = new String[batchSize];
      
      int idx = 0;
      for (IVersionableHandle handle : versionables.subList(batchStart, batchStart + batchSize)) {
        versionableItemId[idx] = handle.getItemId().getUuidValue();
        versionableItemType[idx] = handle.getItemType().getName();
        versionableItemTypeNamespace[idx] = handle.getItemType().getNamespaceURI();
        idx++;
      }
      try
      {
        ScmVersionablePermissionsResult result = scmService.getVersionablePermissions(parms);
        for (ScmVersionablePermissionsReport report : result.getReports()) {
          JSONObject access = null;
          if (Boolean.valueOf(report.getIsReportOfInaccessible()).booleanValue()) {
            access = new JSONObject();
            access.put("type", com.ibm.team.filesystem.cli.client.internal.Messages.Common_UNKNOWN);
          } else {
            access = getReadAccess(report.getReadContext(), repo, config);
          }
          if (access != null) {
            for (String item : report.getItems()) {
              results.put(item, access);
            }
          }
        }
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap("error", e, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      batchStart += batchSize;
      batchSize = batchStart + 50 > versionables.size() ? versionables.size() - batchStart : 50;
    }
    
    return results;
  }
  
  private JSONObject getReadAccess(ScmHandle readContext, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException {
    if (readContext == null) {
      return null;
    }
    
    String name = (String)readContextCache.get(readContext.getItemId());
    if (name == null) {
      IItemType itemType = IItemType.IRegistry.INSTANCE.getItemType(readContext.getItemType(), readContext.getItemNamespace());
      IAuditableHandle handle = (IAuditableHandle)itemType.createItemHandle(UUID.valueOf(readContext.getItemId()), null);
      name = RepoUtil.getReadAccessContextName(handle, repo, config);
      readContextCache.put(readContext.getItemId(), name);
    }
    
    JSONObject access = new JSONObject();
    access.put("type", readContext.getItemType());
    access.put("uuid", readContext.getItemId());
    access.put("name", name);
    return access;
  }
  
  private String toPath(String[] path, boolean isFolder) {
    return StringUtil.createPathString(path) + (isFolder ? Character.valueOf('/') : "");
  }
  
  private String appendPathSegment(String parent, String child, boolean isFolder) {
    StringBuilder builder = new StringBuilder(parent);
    if (builder.charAt(builder.length() - 1) != '/') {
      builder.append('/');
    }
    builder.append(child);
    if (isFolder) {
      builder.append('/');
    }
    return builder.toString();
  }
}
