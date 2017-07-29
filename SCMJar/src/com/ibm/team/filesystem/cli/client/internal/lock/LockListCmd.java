package com.ibm.team.filesystem.cli.client.internal.lock;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.locks.ContributorNameDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.ChoppingIndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.dto.ComponentLocks;
import com.ibm.team.scm.common.internal.dto.ContributorLocks;
import com.ibm.team.scm.common.internal.dto.WorkspaceLocks;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.IScmRestService.ParmsLockSearchCriteria;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;

public class LockListCmd extends AbstractSubcommand implements IOptionSource
{
  public LockListCmd() {}
  
  private class ListLockForRepositoryRequest
  {
    private final String repoUri;
    private final Set<String> streams = new HashSet();
    private final Set<String> componentIds = new HashSet();
    
    public ListLockForRepositoryRequest(String repositoryURL) {
      repoUri = repositoryURL;
    }
    
    public int listLocks(String ownerSelector, int maxResult, boolean verbose, boolean wide, int width, Map<String, WorkspaceDetailsDTO> streamIdToDetails, Map<ParmsWorkspace, ParmsWorkspace> wsToStream)
      throws FileSystemException
    {
      ITeamRepository repo = RepoUtil.getSharedRepository(repoUri, true);
      IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
      
      IContributor owner = null;
      if (ownerSelector != null) {
        try {
          owner = RepoUtil.getContributor(ownerSelector, repo, config);
        } catch (FileSystemException e) {
          if ((!(e instanceof CLIFileSystemClientException)) || 
            (((CLIFileSystemClientException)e).getStatus().getCode() != 25))
          {

            throw e;
          }
        }
        
        if (owner == null) {
          return 0;
        }
      }
      
      IScmRestService.ParmsLockSearchCriteria lockCriteria = new IScmRestService.ParmsLockSearchCriteria();
      streamItemIds = ((String[])streams.toArray(new String[streams.size()]));
      includeStreamAndComponentLocks = Boolean.TRUE;
      length = Integer.valueOf(maxResult);
      if (owner != null) {
        lockedByContributorId = owner.getItemId().getUuidValue();
      }
      
      if (componentIds.size() > 0) {
        componentItemIds = ((String[])componentIds.toArray(new String[componentIds.size()]));
      }
      
      WorkspaceLocks[] result = null;
      try {
        result = scmService.getFindLocks(lockCriteria);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.LockListCmd_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), 
          repo.getRepositoryURI());
      }
      
      JSONArray jEntries = new JSONArray();
      for (WorkspaceLocks locks : result) {
        LockListCmd.this.jsonizeLockEntries(jEntries, locks, owner, wide, width, streamIdToDetails, wsToStream);
      }
      
      LockListCmd.printLockEntries(jEntries, config.getContext().stdout(), wide, width, config);
      return jEntries.size();
    }
    
    public void addStream(String streamId) {
      streams.add(streamId);
    }
    
    public void addComponent(String itemId) {
      componentIds.add(itemId);
    }
  }
  
  IFilesystemRestClient client = null;
  
  private static final String LOCAL = "L";
  
  private static final String REMOTE = "R";
  private static final String UNKNOWN = "U";
  private static final NamedOptionDefinition OPT_OWNER = new NamedOptionDefinition("o", "owner", 1);
  private static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("c", "component", 1);
  
  public Options getOptions() throws com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException { Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    options.addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP);
    options.addOption(CommonOptions.OPT_WIDE, CommonOptions.OPT_WIDE_HELP);
    options.addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP);
    options.addOption(CommonOptions.OPT_STREAM_SELECTOR, CommonOptions.OPT_STREAM_SELECTOR_HELP);
    options.addOption(OPT_COMPONENT, CommonOptions.OPT_COMPONENT_SELECTOR_HELP);
    options.addOption(OPT_OWNER, Messages.LockListCmd_OptOwnerUserIdHelp);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    client = SubcommandUtil.setupDaemon(config);
    

    Map<ParmsWorkspace, ParmsWorkspace> wsToStream = findSandboxWorkspaceToStreamMappings(cli);
    

    IScmCommandLineArgument componentSelector = getComponentSelector(cli);
    


    Set<String> componentIds = new HashSet();
    Set<String> streamIds = new HashSet();
    IUuidAliasRegistry.IUuidAlias compAlias; Object wsCompDTO; Map<String, WorkspaceDetailsDTO> streamIdToDetails; if (cli.hasOption(CommonOptions.OPT_STREAM_SELECTOR))
    {
      IScmCommandLineArgument streamSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_STREAM_SELECTOR), config);
      ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, streamSelector);
      SubcommandUtil.validateArgument(streamSelector, RepoUtil.ItemType.STREAM);
      IWorkspace stream = RepoUtil.getWorkspace(streamSelector.getItemSelector(), false, true, repo, config);
      
      Map<String, WorkspaceDetailsDTO> streamIdToDetails = fetchStreamDetails(Collections.singletonList(new ParmsWorkspace(repo.getRepositoryURI(), stream.getItemId().getUuidValue())));
      streamIds.add(stream.getItemId().getUuidValue());
      
      if (componentSelector == null)
      {
        WorkspaceDetailsDTO streamDetails = (WorkspaceDetailsDTO)streamIdToDetails.get(stream.getItemId().getUuidValue());
        for (Object wsCompDTO : streamDetails.getComponents()) {
          componentIds.add(((WorkspaceComponentDTO)wsCompDTO).getItemId());
        }
      }
      else {
        compAlias = RepoUtil.lookupUuidAndAlias(componentSelector.getItemSelector());
        WorkspaceDetailsDTO streamDetails = (WorkspaceDetailsDTO)streamIdToDetails.get(stream.getItemId().getUuidValue());
        for (Iterator localIterator2 = streamDetails.getComponents().iterator(); localIterator2.hasNext();) { wsCompDTO = localIterator2.next();
          WorkspaceComponentDTO component = (WorkspaceComponentDTO)wsCompDTO;
          if ((aliasMatchesId(compAlias, component)) || (componentSelector.getItemSelector().equals(component.getName()))) {
            componentIds.add(component.getItemId());
            break;
          }
        }
      }
    }
    else {
      if (wsToStream.isEmpty()) {
        throw StatusHelper.argSyntax(Messages.LockAcquireCmd_UNMATCHED_STREAM);
      }
      List<ParmsWorkspace> uniqueStreams = getUniqueStreams(wsToStream.values());
      streamIdToDetails = fetchStreamDetails(uniqueStreams);
      
      if (componentSelector == null) {
        List<ShareDTO> shareDTOList = RepoUtil.getSharesInSandbox(null, client, config);
        for (ShareDTO shareDTO : shareDTOList) {
          componentIds.add(shareDTO.getComponentItemId());
        }
        streamIds.addAll(streamIdToDetails.keySet());
      }
      else
      {
        IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(componentSelector.getItemSelector());
        
        for (WorkspaceDetailsDTO streamDetails : streamIdToDetails.values()) {
          for (wsCompDTO = streamDetails.getComponents().iterator(); ((Iterator)wsCompDTO).hasNext();) { Object wsCompDTO = ((Iterator)wsCompDTO).next();
            WorkspaceComponentDTO component = (WorkspaceComponentDTO)wsCompDTO;
            if ((aliasMatchesId(compAlias, component)) || (componentSelector.getItemSelector().equals(component.getName())))
            {
              componentIds.add(component.getItemId());
              streamIds.add(streamDetails.getItemId());
              break;
            }
          }
        }
      }
    }
    

    if (componentIds.isEmpty()) {
      if ((cli.hasOption(OPT_COMPONENT)) && (componentSelector != null))
      {
        throw StatusHelper.argSyntax(NLS.bind(Messages.LockListCmd_UNMATCHED_COMP, componentSelector.getItemSelector()));
      }
      
      config.getContext().stdout().println(Messages.LockListCmd_NO_LOCKS_TO_LIST);
      return;
    }
    


    Map<String, ListLockForRepositoryRequest> repoUriToRequest = createListLockRequests(streamIdToDetails, streamIds, componentIds);
    
    String ownerSelector = null;
    if (cli.hasOption(OPT_OWNER)) {
      IScmCommandLineArgument ownerId = ScmCommandLineArgument.create(cli.getOptionValue(OPT_OWNER), config);
      ownerSelector = ownerId.getItemSelector();
    }
    
    boolean verbose = cli.hasOption(CommonOptions.OPT_VERBOSE);
    boolean wide = cli.hasOption(CommonOptions.OPT_WIDE);
    int width = SubcommandUtil.getTerminalWidth(config);
    int maxResult = RepoUtil.getMaxResultsOption(cli);
    
    int lockEntries = 0;
    for (Map.Entry<String, ListLockForRepositoryRequest> entry : repoUriToRequest.entrySet())
    {
      lockEntries = lockEntries + ((ListLockForRepositoryRequest)entry.getValue()).listLocks(ownerSelector, maxResult, verbose, wide, width, streamIdToDetails, wsToStream);
    }
    
    if (lockEntries == 0) {
      config.getContext().stdout().println(Messages.LockListCmd_NO_LOCKS_TO_LIST);
    }
  }
  
  private IScmCommandLineArgument getComponentSelector(ICommandLine cli) throws FileSystemException
  {
    IScmCommandLineArgument componentSelector = null;
    if (cli.hasOption(OPT_COMPONENT)) {
      componentSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(componentSelector, RepoUtil.ItemType.COMPONENT);
    }
    return componentSelector;
  }
  



  private Map<String, ListLockForRepositoryRequest> createListLockRequests(Map<String, WorkspaceDetailsDTO> streamIdToDetails, Set<String> streamIds, Set<String> componentIds)
  {
    Map<String, ListLockForRepositoryRequest> repoUriToRequest = new HashMap();
    Iterator localIterator2; for (Iterator localIterator1 = streamIds.iterator(); localIterator1.hasNext(); 
        






        localIterator2.hasNext())
    {
      String streamId = (String)localIterator1.next();
      WorkspaceDetailsDTO stream = (WorkspaceDetailsDTO)streamIdToDetails.get(streamId);
      ListLockForRepositoryRequest request = (ListLockForRepositoryRequest)repoUriToRequest.get(stream.getRepositoryURL());
      if (request == null) {
        request = new ListLockForRepositoryRequest(stream.getRepositoryURL());
        repoUriToRequest.put(stream.getRepositoryURL(), request);
      }
      request.addStream(streamId);
      localIterator2 = stream.getComponents().iterator(); continue;Object o = localIterator2.next();
      WorkspaceComponentDTO wc = (WorkspaceComponentDTO)o;
      if (componentIds.contains(wc.getItemId())) {
        request.addComponent(wc.getItemId());
      }
    }
    
    return repoUriToRequest;
  }
  
  private boolean aliasMatchesId(IUuidAliasRegistry.IUuidAlias alias, WorkspaceComponentDTO component) {
    if (alias == null) {
      return false;
    }
    return alias.getUuid().getUuidValue().equals(component.getItemId());
  }
  
  private List<ParmsWorkspace> getUniqueStreams(Collection<ParmsWorkspace> streams) {
    return new ArrayList(new HashSet(streams));
  }
  
  private Map<String, WorkspaceDetailsDTO> fetchStreamDetails(List<ParmsWorkspace> streams) throws FileSystemException {
    Map<String, WorkspaceDetailsDTO> streamIdToDetails = new HashMap();
    for (ParmsWorkspace stream : streams) {
      RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    }
    List<WorkspaceDetailsDTO> streamDetailsList = RepoUtil.getWorkspaceDetails(streams, client, config);
    for (WorkspaceDetailsDTO streamDetails : streamDetailsList) {
      streamIdToDetails.put(streamDetails.getItemId(), streamDetails);
    }
    return streamIdToDetails;
  }
  
  private Map<ParmsWorkspace, ParmsWorkspace> findSandboxWorkspaceToStreamMappings(ICommandLine cli)
    throws FileSystemException, CLIFileSystemClientException
  {
    Map<ParmsWorkspace, ParmsWorkspace> wsToStream = new HashMap();
    

    List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
    if ((wsInSandboxList.isEmpty()) && (!cli.hasOption(CommonOptions.OPT_STREAM_SELECTOR))) {
      throw StatusHelper.argSyntax(Messages.LockListCmd_NO_SANDBOX);
    }
    List<ParmsWorkspace> wsList = new ArrayList(wsInSandboxList.size());
    for (ISandboxWorkspace wsInSandbox : wsInSandboxList) {
      try
      {
        String uri = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
          Collections.singletonList(wsInSandbox));
        RepoUtil.login(config, client, config.getConnectionInfo(uri));
        wsList.add(new ParmsWorkspace(uri, wsInSandbox.getWorkspaceItemId()));
      } catch (FileSystemException e) {
        if (!cli.hasOption(CommonOptions.OPT_STREAM_SELECTOR)) {
          throw e;
        }
      }
    }
    

    if (wsList.size() > 0) {
      List<WorkspaceDetailsDTO> wsDetailsList = RepoUtil.getWorkspaceDetails(wsList, client, config);
      for (WorkspaceDetailsDTO wsDetails : wsDetailsList) {
        ParmsWorkspace stream = getFlowStream(wsDetails);
        if (stream != null) {
          wsToStream.put(new ParmsWorkspace(wsDetails.getRepositoryURL(), wsDetails.getItemId()), stream);
        }
      }
    }
    return wsToStream;
  }
  
  private ParmsWorkspace getFlowStream(WorkspaceDetailsDTO wsDetails) throws FileSystemException
  {
    ParmsWorkspace ws = RepoUtil.getFlowTarget(wsDetails, false, false);
    

    WorkspaceDetailsDTO wsFlowDetails = null;
    if (ws != null) {
      wsFlowDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    }
    
    if ((wsFlowDetails != null) && (wsFlowDetails.isStream())) {
      return ws;
    }
    
    return null;
  }
  

  private void jsonizeLockEntries(JSONArray jEntries, WorkspaceLocks wsLocks, IContributor owner, boolean wide, int width, Map<String, WorkspaceDetailsDTO> streamIdToDetails, Map<ParmsWorkspace, ParmsWorkspace> wsToStream)
    throws FileSystemException
  {
    WorkspaceDetailsDTO streamDetails = (WorkspaceDetailsDTO)streamIdToDetails.get(wsLocks.getWorkspace().getItemId().getUuidValue());
    

    Map<String, WorkspaceComponentDTO> componentDetails = new HashMap();
    for (WorkspaceComponentDTO componentDTO : streamDetails.getComponents()) {
      componentDetails.put(componentDTO.getItemId(), componentDTO);
    }
    
    ITeamRepository repo = RepoUtil.getSharedRepository(streamDetails.getRepositoryURL(), true);
    IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
    
    Map<String, IContributor> contribIdToObject = new HashMap();
    if (owner != null) {
      contribIdToObject.put(owner.getItemId().getUuidValue(), owner);
    }
    
    if (wsLocks.isStreamLock()) {
      JSONObject jEntry = new JSONObject();
      
      jEntry.put("owner", streamDetails.getLockedBy().getUserId());
      
      stream = JSONPrintUtil.jsonize(streamDetails.getName(), streamDetails.getItemId(), 
        streamDetails.getRepositoryURL(), streamDetails.isStream());
      jEntry.put("stream", stream);
      jEntries.add(jEntry); return;
    }
    
    JSONObject comp;
    for (JSONObject stream = wsLocks.getComponentLocks().iterator(); stream.hasNext(); 
        


























        comp.hasNext())
    {
      ComponentLocks compLocks = (ComponentLocks)stream.next();
      String compId = compLocks.getComponent().getItemId().getUuidValue();
      
      List<ContributorLocks> contribLockList = new ArrayList();
      if (owner != null) {
        contribLockList.add(compLocks.getContributorLock(owner));
      } else {
        contribLockList.addAll(compLocks.getContributorLocks());
      }
      

      WorkspaceComponentDTO componentDTO = (WorkspaceComponentDTO)componentDetails.get(compId);
      if ((contribLockList.isEmpty()) && (componentDTO != null) && (componentDTO.getLockedBy() != null)) {
        JSONObject jEntry = new JSONObject();
        
        jEntry.put("owner", componentDTO.getLockedBy().getUserId());
        
        comp = JSONPrintUtil.jsonize(componentDTO.getName(), componentDTO.getItemId(), streamDetails.getRepositoryURL());
        jEntry.put("component", comp);
        
        JSONObject stream = JSONPrintUtil.jsonize(streamDetails.getName(), streamDetails.getItemId(), 
          streamDetails.getRepositoryURL(), streamDetails.isStream());
        jEntry.put("stream", stream);
        jEntries.add(jEntry);
        return;
      }
      

      comp = contribLockList.iterator(); continue;ContributorLocks contribLocks = (ContributorLocks)comp.next();
      for (IVersionableHandle verHandle : contribLocks.getVersionables()) {
        ShareableDTO shareable = findLocalVersionable(streamDetails, wsToStream, compId, verHandle);
        

        String source = "R";
        String verPath = null;
        if (shareable != null) {
          source = "L";
          verPath = StringUtil.createPathString(shareable.getRelativePath().getSegments());
        } else {
          String itemType = verHandle.getItemType().getNamespaceURI() + "." + verHandle.getItemType().getName();
          VersionableDTO verDTO = null;
          try {
            verDTO = RepoUtil.getVersionableById(scmService, streamDetails.getItemId(), compId, 
              verHandle.getItemId().getUuidValue(), null, itemType, config);
            verPath = verDTO.getPath();
          } catch (FileSystemException e) {
            if (((e instanceof CLIFileSystemClientException)) && 
              (((CLIFileSystemClientException)e).getStatus().getCode() == 25)) {
              source = "U";
              verPath = Messages.LockListCmd_Unknown;
            } else {
              throw e;
            }
          }
        }
        

        JSONObject jEntry = new JSONObject();
        jEntry.put("nameSource", source);
        

        JSONObject jVer = JSONPrintUtil.jsonize(verPath, verHandle.getItemId().getUuidValue(), 
          streamDetails.getRepositoryURL());
        jEntry.put("versionable", jVer);
        

        String contrib = null;
        IContributor contributor = (IContributor)contribIdToObject.get(contribLocks.getContributor().getItemId().getUuidValue());
        if (contributor == null) {
          try {
            contributor = (IContributor)RepoUtil.getItem(IContributor.ITEM_TYPE, 
              contribLocks.getContributor().getItemId(), repo, config);
            contrib = contributor.getUserId();
          } catch (Exception localException) {
            contrib = Messages.LockListCmd_Unknown;
          }
        } else {
          contrib = contributor.getUserId();
        }
        jEntry.put("owner", contrib);
        

        WorkspaceComponentDTO compMatched = (WorkspaceComponentDTO)componentDetails.get(compId);
        JSONObject comp = null;
        if (compMatched != null) {
          comp = JSONPrintUtil.jsonize(compMatched.getName(), compMatched.getItemId(), streamDetails.getRepositoryURL());
        } else {
          comp = JSONPrintUtil.jsonize(Messages.LockListCmd_Unknown, compId, streamDetails.getRepositoryURL());
        }
        jEntry.put("component", comp);
        

        JSONObject stream = JSONPrintUtil.jsonize(streamDetails.getName(), streamDetails.getItemId(), 
          streamDetails.getRepositoryURL(), streamDetails.isStream());
        jEntry.put("stream", stream);
        
        jEntries.add(jEntry);
      }
    }
  }
  



  private ShareableDTO findLocalVersionable(WorkspaceDetailsDTO streamDetails, Map<ParmsWorkspace, ParmsWorkspace> wsToStream, String compId, IVersionableHandle verHandle)
  {
    ShareableDTO shareable = null;
    for (Map.Entry<ParmsWorkspace, ParmsWorkspace> entry : wsToStream.entrySet()) {
      if (getValueworkspaceItemId.equals(streamDetails.getItemId())) {
        shareable = findLocalVersionable((ParmsWorkspace)entry.getKey(), compId, verHandle);
        if (shareable != null)
        {
          IPath sandboxPath = new Path(shareable.getSandboxPath());
          if (sandboxPath.isPrefixOf(new Path(config.getCurrentWorkingDirectory().getAbsolutePath()))) {
            break;
          }
          shareable = null;
        }
      }
    }
    

    return shareable;
  }
  
  private ShareableDTO findLocalVersionable(ParmsWorkspace ws, String compId, IVersionableHandle verHandle)
  {
    ShareableDTO shareableDTO = null;
    try {
      shareableDTO = RepoUtil.findLocalVersionable(ws, compId, verHandle.getItemId().getUuidValue(), 
        SubcommandUtil.getVersionableItemType(verHandle.getItemType()), client, null);
    }
    catch (TeamRepositoryException localTeamRepositoryException) {}
    

    return shareableDTO;
  }
  

  public static void printLockEntries(JSONArray jEntries, OutputStream out, boolean wide, int width, IScmClientConfiguration config)
  {
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jEntries);
      return;
    }
    
    List<String> versionableLocks = new ArrayList();
    List<String> streamLocks = new ArrayList();
    List<String> componentLocks = new ArrayList();
    
    for (Object obj : jEntries) {
      JSONObject entry = (JSONObject)obj;
      
      String sourceString = "";
      String versionableString = "";
      String ownerString = "";
      String componentString = "";
      String streamString = "";
      
      sourceString = (String)entry.get("nameSource");
      ownerString = (String)entry.get("owner");
      
      JSONObject stream = (JSONObject)entry.get("stream");
      RepoUtil.ItemType itemType = com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.getWorkspaceType((String)stream.get("type"));
      
      streamString = AliasUtil.selector((String)stream.get("name"), 
        UUID.valueOf((String)stream.get("uuid")), (String)stream.get("url"), 
        itemType);
      
      JSONObject versionable = (JSONObject)entry.get("versionable");
      if (versionable != null) {
        versionableString = AliasUtil.selector((String)versionable.get("name"), 
          UUID.valueOf((String)versionable.get("uuid")), (String)versionable.get("url"), 
          RepoUtil.ItemType.VERSIONABLE);
      }
      
      JSONObject component = (JSONObject)entry.get("component");
      if (component != null) {
        componentString = AliasUtil.selector((String)component.get("name"), 
          UUID.valueOf((String)component.get("uuid")), (String)component.get("url"), 
          RepoUtil.ItemType.COMPONENT);
      }
      

      if ((versionable == null) && (component == null))
      {
        streamLocks.add(streamString);
        streamLocks.add(ownerString);
      } else if ((versionable == null) && (component != null))
      {
        componentLocks.add(componentString);
        componentLocks.add(ownerString);
        componentLocks.add(streamString);
      }
      else {
        versionableLocks.add(sourceString);
        versionableLocks.add(versionableString);
        versionableLocks.add(ownerString);
        versionableLocks.add(componentString);
        versionableLocks.add(streamString);
      }
    }
    
    IndentingPrintStream ps = wide ? 
      new IndentingPrintStream(out) : 
      new ChoppingIndentingPrintStream(out, width);
    
    if (!streamLocks.isEmpty()) {
      ps.println(Messages.LockListCmd_STREAM_LOCKS);
      StringUtil.printTable(ps.indent(), 2, true, (CharSequence[])streamLocks.toArray(new String[streamLocks.size()]));
      ps.println();
    }
    
    if (!componentLocks.isEmpty()) {
      ps.println(Messages.LockListCmd_COMPONENT_LOCKS);
      StringUtil.printTable(ps.indent(), 3, true, (CharSequence[])componentLocks.toArray(new String[componentLocks.size()]));
      ps.println();
    }
    
    if (!versionableLocks.isEmpty()) {
      ps.println(Messages.LockListCmd_FILE_LOCKS);
      StringUtil.printTable(ps.indent(), 5, true, (CharSequence[])versionableLocks.toArray(new String[versionableLocks.size()]));
    }
  }
}
