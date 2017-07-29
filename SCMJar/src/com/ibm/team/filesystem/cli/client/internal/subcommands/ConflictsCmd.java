package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.Property;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.FileLineDelimiter;
import com.ibm.team.filesystem.common.IFileContent;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.ISymbolicLink;
import com.ibm.team.filesystem.common.internal.rest.client.resource.FilePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ConflictSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.LocalConflictSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.UnresolvedFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.client.IVersionableManager;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.VersionablePermissionDeniedException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.osgi.util.NLS;

public class ConflictsCmd extends AbstractSubcommand
{
  boolean quiet;
  public ConflictsCmd() {}
  
  static enum Mode
  {
    MINE,  PROPOSED,  ANCESTOR,  COMPARE;
  }
  



  public void run()
    throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    
    quiet = subargs.hasOption(CommonOptions.OPT_QUIET);
    
    IScmCommandLineArgument mine = ScmCommandLineArgument.create(subargs.getOptionValue(ConflictsCmdOpts.OPT_CONFLICTS_MINE, null), config);
    IScmCommandLineArgument proposed = ScmCommandLineArgument.create(subargs.getOptionValue(ConflictsCmdOpts.OPT_CONFLICTS_PROPOSED, null), config);
    IScmCommandLineArgument ancestor = ScmCommandLineArgument.create(subargs.getOptionValue(ConflictsCmdOpts.OPT_CONFLICTS_ANCESTOR, null), config);
    IScmCommandLineArgument compare = ScmCommandLineArgument.create(subargs.getOptionValue(ConflictsCmdOpts.OPT_CONFLICTS_EXTERNAL_COMPARE, null), config);
    
    Mode conflictMode = Mode.MINE;
    IScmCommandLineArgument conflictItem = null;
    int modeCount = 0;
    if (mine != null) {
      conflictMode = Mode.MINE;
      conflictItem = mine;
      modeCount++;
    }
    if (proposed != null) {
      conflictMode = Mode.PROPOSED;
      conflictItem = proposed;
      modeCount++;
    }
    if (ancestor != null) {
      conflictMode = Mode.ANCESTOR;
      conflictItem = ancestor;
      modeCount++;
    }
    if (compare != null) {
      conflictMode = Mode.COMPARE;
      conflictItem = compare;
      modeCount++;
    }
    
    if (modeCount > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ConflictsCmd_1, 
        new String[] {
        ConflictsCmdOpts.OPT_CONFLICTS_MINE.getName(), 
        ConflictsCmdOpts.OPT_CONFLICTS_PROPOSED.getName(), 
        ConflictsCmdOpts.OPT_CONFLICTS_ANCESTOR.getName(), 
        ConflictsCmdOpts.OPT_CONFLICTS_EXTERNAL_COMPARE.getName() }));
    }
    
    boolean showContent = subargs.hasOption(ConflictsCmdOpts.OPT_CONTENT);
    boolean allProperties = subargs.hasOption(ConflictsCmdOpts.OPT_ALL_PROPERTIES);
    boolean listPropertyNames = subargs.hasOption(ConflictsCmdOpts.OPT_PROPERTY_NAMES);
    String propertyName = subargs.getOption(ConflictsCmdOpts.OPT_PROPERTY_NAME_VALUE, null);
    
    Property showProperty = Property.NONE;
    int propertyOptions = 0;
    if (allProperties) {
      showProperty = Property.ALL;
      propertyOptions++;
    }
    if (listPropertyNames) {
      showProperty = Property.LIST_NAMES;
      propertyOptions++;
    }
    if (propertyName != null) {
      showProperty = Property.NAME_VALUE;
      propertyOptions++;
    }
    

    if (((showContent) || (propertyOptions > 0)) && (modeCount == 0)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ConflictsCmd_1, 
        new String[] {
        ConflictsCmdOpts.OPT_CONFLICTS_MINE.getName(), 
        ConflictsCmdOpts.OPT_CONFLICTS_PROPOSED.getName(), 
        ConflictsCmdOpts.OPT_CONFLICTS_ANCESTOR.getName(), 
        ConflictsCmdOpts.OPT_CONFLICTS_EXTERNAL_COMPARE.getName() }));
    }
    

    if (propertyOptions > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ConflictsCmd_1, 
        new String[] {
        ConflictsCmdOpts.OPT_ALL_PROPERTIES.getName(), 
        ConflictsCmdOpts.OPT_PROPERTY_NAMES.getName(), 
        ConflictsCmdOpts.OPT_PROPERTY_NAME_VALUE.getName() }));
    }
    
    String externalCompareTool = null;
    if (conflictMode == Mode.COMPARE)
    {
      if (showProperty != Property.NONE) {
        throw StatusHelper.argSyntax(Messages.ResolveCmd_CANNOT_EXTERNAL_COMPARE_PROPERTY);
      }
      
      externalCompareTool = DiffCmd.getExternalCompareTool((com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration)config, true);
    }
    


    if ((!showContent) && (propertyOptions == 0)) {
      showContent = true;
      if (conflictMode != Mode.COMPARE) {
        showProperty = Property.ALL;
      }
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    List<ISandboxWorkspace> wsInSandboxList = null;
    if (modeCount != 0) {
      ILocation pathLoc = SubcommandUtil.makeAbsolutePath(config, conflictItem.getItemSelector());
      if (SubcommandUtil.exists(pathLoc, null)) {
        wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, new org.eclipse.core.runtime.Path(pathLoc.toOSString()), config);
      }
    }
    

    if (wsInSandboxList == null) {
      wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
    }
    

    List<ParmsWorkspace> wsList = new ArrayList(wsInSandboxList.size());
    for (ISandboxWorkspace wsInSandbox : wsInSandboxList) {
      String uri = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
        Collections.singletonList(wsInSandbox));
      RepoUtil.login(config, client, config.getConnectionInfo(uri));
      wsList.add(new ParmsWorkspace(uri, wsInSandbox.getWorkspaceItemId()));
    }
    
    if (modeCount == 0) {
      if (subargs.hasOption(ConflictsCmdOpts.OPT_LOCAL))
      {
        List<LocalConflictResult> conflictResults = findLocalConflicts(wsList, client, config);
        showLocalConflictItems(conflictResults, config);
      }
      else {
        List<ConflictResult> conflictResults = findConflicts(wsList, client, config);
        showConflictItems(conflictResults, config);
      }
      return;
    }
    
    if (subargs.hasOption(ConflictsCmdOpts.OPT_LOCAL)) {
      List<LocalConflictResult> conflictResults = findLocalConflicts(wsList, client, config);
      showLocalConflictItemInfo(conflictResults, conflictItem, conflictMode, showContent, showProperty, 
        propertyName, client, config, externalCompareTool);
    } else {
      List<ConflictResult> conflictResults = findConflicts(wsList, client, config);
      showConflictItemInfo(conflictResults, conflictItem, conflictMode, showContent, showProperty, 
        propertyName, client, config, externalCompareTool);
    }
  }
  
  private static void showConflictItems(List<ConflictResult> conflictResults, IScmClientConfiguration config) throws FileSystemException {
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    if ((conflictResults.size() == 0) && (!config.isJSONEnabled())) {
      printStream.println(Messages.ConflictsCmd_NoConflictsFound);
      return;
    }
    

    Map<ITeamRepository, List<ConflictSyncDTO>> conflictsMap = new HashMap();
    for (ConflictResult conflictResult : conflictResults) {
      List<ConflictSyncDTO> conflictList = (List)conflictsMap.get(repo);
      if (conflictList == null) {
        conflictList = new ArrayList();
        conflictsMap.put(repo, conflictList);
      }
      conflictList.add(conflict);
    }
    

    boolean printHeader = true;
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    for (Map.Entry<ITeamRepository, List<ConflictSyncDTO>> entry : conflictsMap.entrySet()) {
      PendingChangesUtil.printConflictItems((List)entry.getValue(), ((ITeamRepository)entry.getKey()).getRepositoryURI(), 
        printHeader, options, printStream, config);
      printHeader = false;
    }
    
    if (!config.isJSONEnabled()) {
      throw StatusHelper.conflict(Messages.ConflictsCmd_10);
    }
  }
  
  private static void showLocalConflictItems(List<LocalConflictResult> conflictResults, IScmClientConfiguration config) throws FileSystemException {
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    if ((conflictResults.size() == 0) && (!config.isJSONEnabled())) {
      printStream.println(Messages.ConflictsCmd_NoLocalConflictsFound);
      return;
    }
    

    Map<ITeamRepository, List<LocalConflictSyncDTO>> conflictsMap = new HashMap();
    for (LocalConflictResult conflictResult : conflictResults) {
      List<LocalConflictSyncDTO> conflictList = (List)conflictsMap.get(repo);
      if (conflictList == null) {
        conflictList = new ArrayList();
        conflictsMap.put(repo, conflictList);
      }
      conflictList.add(conflict);
    }
    

    boolean printHeader = true;
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    for (Map.Entry<ITeamRepository, List<LocalConflictSyncDTO>> entry : conflictsMap.entrySet()) {
      PendingChangesUtil.printLocalConflictItems(
        (List)entry.getValue(), 
        ((ITeamRepository)entry.getKey()).getRepositoryURI(), 
        printHeader, 
        options, 
        printStream, 
        config);
      printHeader = false;
    }
  }
  



  private void showConflictItemInfo(List<ConflictResult> conflictResults, IScmCommandLineArgument conflictItem, Mode conflictMode, boolean showContent, Property showProperty, String propertyName, IFilesystemRestClient client, IScmClientConfiguration config, String externalCompareTool)
    throws FileSystemException
  {
    ResourcePropertiesDTO resProp = null;
    IUuidAliasRegistry.IUuidAlias conflictAlias = null;
    

    ILocation path = SubcommandUtil.makeAbsolutePath(config, conflictItem.getItemSelector());
    if (!SubcommandUtil.exists(path, null)) {
      SubcommandUtil.validateArgument(conflictItem, RepoUtil.ItemType.VERSIONABLE);
      conflictAlias = RepoUtil.lookupUuidAndAlias(conflictItem.getItemSelector());
    } else {
      resProp = RepoUtil.getResourceProperties(path.toOSString(), client, config);
    }
    
    ConflictResult result = findConflict(conflictResults, conflictAlias, resProp, config);
    if (result == null) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ConflictsCmd_2, conflictItem.getItemSelector()));
    }
    

    String itemId = null;
    String stateId = null;
    String stateMessageOnError = Messages.ConflictsCmd_ItemState_Deleted;
    switch (conflictMode) {
    case ANCESTOR: 
      itemId = itemId;
      stateId = stateId;
      break;
    case COMPARE: 
      itemId = conflict.getVersionableItemId();
      stateId = conflict.getProposedContributorVersionableStateId();
      break;
    case MINE: 
      itemId = conflict.getVersionableItemId();
      stateId = conflict.getCommonAncestorVersionableStateId();
      stateMessageOnError = Messages.ConflictsCmd_NoCommonAncestor;
      break;
    case PROPOSED: 
      ResolveCmd.resolveExternally(conflict, client, config, 
        config.getSubcommandCommandLine(), externalCompareTool);
      return;
    }
    
    if (stateId == null) {
      config.getContext().stdout().println(stateMessageOnError);
      return;
    }
    
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    
    if (showContent) {
      printStream.println(Messages.ConflictsCmd_Content);
      showContent(
        conflict.getVersionableItemType(), 
        itemId, 
        stateId, 
        path, 
        repo, 
        printStream.indent(), 
        config, 
        client, 
        resProp);
    }
    
    if (showProperty != Property.NONE) {
      IVersionableHandle state = RepoUtil.getVersionableHandle(repo, itemId, stateId, conflict.getVersionableItemType(), config);
      printStream.println(Messages.ConflictsCmd_Properties);
      showProperties(state, path, showProperty, propertyName, repo, printStream.indent(), config);
    }
  }
  










  private void showLocalConflictItemInfo(List<LocalConflictResult> conflictResults, IScmCommandLineArgument conflictItem, Mode conflictMode, boolean showContent, Property showProperty, String propertyName, IFilesystemRestClient client, IScmClientConfiguration config, String externalCompareTool)
    throws FileSystemException
  {
    ResourcePropertiesDTO resProp = null;
    IUuidAliasRegistry.IUuidAlias conflictAlias = null;
    

    ILocation path = SubcommandUtil.makeAbsolutePath(config, conflictItem.getItemSelector());
    if (!SubcommandUtil.exists(path, null)) {
      SubcommandUtil.validateArgument(conflictItem, RepoUtil.ItemType.VERSIONABLE);
      conflictAlias = RepoUtil.lookupUuidAndAlias(conflictItem.getItemSelector());
    } else {
      resProp = RepoUtil.getResourceProperties(path.toOSString(), client, config);
    }
    
    LocalConflictResult result = findLocalConflict(conflictResults, conflictAlias, resProp, config);
    if (result == null) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ConflictsCmd_2, conflictItem.getItemSelector()));
    }
    

    String itemId = null;
    String stateId = null;
    String stateMessageOnError = null;
    switch (conflictMode) {
    case ANCESTOR: 
      itemId = itemId;
      break;
    case COMPARE: 
      itemId = conflict.getVersionableItemId();
      stateId = conflict.getProposedContributorVersionableStateId();
      break;
    case MINE: 
      itemId = conflict.getVersionableItemId();
      stateId = conflict.getCommonAncestorVersionableStateId();
      stateMessageOnError = Messages.ConflictsCmd_NoCommonAncestor;
      break;
    case PROPOSED: 
      ResolveCmd.resolveExternally(conflict, client, config, 
        config.getSubcommandCommandLine(), externalCompareTool);
      return;
    }
    
    if ((stateMessageOnError != null) && (stateId == null)) {
      config.getContext().stdout().println(stateMessageOnError);
      return;
    }
    
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    
    if ((showContent) && (!config.isJSONEnabled())) {
      printStream.println(Messages.ConflictsCmd_Content);
      showContent(
        conflict.getVersionableItemType(), 
        itemId, 
        stateId, 
        path, 
        repo, 
        printStream.indent(), 
        config, 
        client, 
        resProp);
    }
    
    if (showProperty != Property.NONE) {
      if (!config.isJSONEnabled()) {
        printStream.println(Messages.ConflictsCmd_Properties);
      }
      if (stateId != null) {
        IVersionableHandle state = RepoUtil.getVersionableHandle(repo, itemId, stateId, conflict.getVersionableItemType(), config);
        showProperties(state, path, showProperty, propertyName, repo, printStream.indent(), config);


      }
      else if (resProp != null) {
        Map<String, String> properties = new HashMap();
        if (resProp.getUserProperties() != null) {
          properties.putAll(resProp.getUserProperties());
        }
        if (resProp.getFileProperties() != null) {
          addInternalProperties(properties, resProp.getFileProperties());
        }
        internalShowProperties(showProperty, propertyName, printStream.indent(), properties);
      }
    }
  }
  

  private void addInternalProperties(Map<String, String> properties, FilePropertiesDTO fileProperties)
  {
    properties.put("jazz.executable", Boolean.toString(fileProperties.isExecutable()));
    properties.put("jazz.line-delimiter", fileProperties.getLineDelimiter().toString());
    properties.put("jazz.mime", fileProperties.getContentType());
    properties.put("jazz.encoding", fileProperties.getEncoding());
  }
  







  private void showContent(String itemType, String itemId, String stateId, ILocation path, ITeamRepository repo, IndentingPrintStream printStream, IScmClientConfiguration config, IFilesystemRestClient client, ResourcePropertiesDTO resProp)
    throws FileSystemException
  {
    ByteArrayOutputStream out = null;
    String fileContents = null;
    try {
      out = new ByteArrayOutputStream();
      
      if (itemType.equals("file")) {
        if (stateId != null) {
          RepoUtil.httpDownloadFile(repo, "-", "-", itemId, stateId, out, config);
        }
        else {
          File file = new File(path.toOSString());
          if (!file.exists())
          {
            return;
          }
          Charset streamEncoding = getEncodingFor(resProp);
          InputStream in = null;
          try {
            in = new BufferedInputStream(new FileInputStream(file));
            RepoUtil.transfer(in, out);
          }
          catch (IOException localIOException1) {
            return;
          }
          finally {}
          try
          {
            fileContents = out.toString(streamEncoding.name());
          }
          catch (UnsupportedEncodingException localUnsupportedEncodingException1) {
            return;
          }
        }
      } else if ((itemType.equals("symbolic_link")) && (stateId != null)) {
        IVersionableHandle verHandle = RepoUtil.getVersionableHandle(repo, itemId, stateId, itemType, config);
        if (verHandle != null)
        {
          try {
            symbolicLink = (ISymbolicLink)SCMPlatform.getWorkspaceManager(repo).versionableManager()
              .fetchCompleteState(verHandle, null);
          } catch (VersionablePermissionDeniedException localVersionablePermissionDeniedException) { ISymbolicLink symbolicLink;
            throw StatusHelper.permissionFailure(Messages.Common_VERSIONABLE_PERMISSSION_DENIED);
          } catch (TeamRepositoryException e) {
            throw StatusHelper.wrap(Messages.ConflictsCmd_4, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
          }
          try {
            ISymbolicLink symbolicLink;
            out.write(symbolicLink.getTarget().getBytes(Charset.defaultCharset().name()));
            out.flush();
          } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage(), e);
          } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
          }
        }
      } else {
        return;
      }
      
      if (fileContents != null) {
        printStream.print(fileContents);
      } else {
        printStream.println(out.toString());
      }
    } finally {
      safeClose(out); } safeClose(out);
  }
  




  private final void safeClose(Closeable toClose)
  {
    if (toClose != null) {
      try {
        toClose.close();
      }
      catch (IOException localIOException) {}
    }
  }
  
  private Charset getEncodingFor(ResourcePropertiesDTO resProp) {
    if ((resProp != null) && (resProp.getFileProperties() != null)) {
      String encoding = resProp.getFileProperties().getEncoding();
      return getEncoding(encoding);
    }
    
    return Charset.defaultCharset();
  }
  
  private Charset getEncoding(String encoding) {
    if ((encoding != null) && (encoding.length() > 0)) {
      try {
        if (encoding != null) {
          return Charset.forName(encoding);
        }
      }
      catch (IllegalCharsetNameException localIllegalCharsetNameException) {}catch (UnsupportedCharsetException localUnsupportedCharsetException) {}
    }
    

    return Charset.defaultCharset();
  }
  
  private void showProperties(IVersionableHandle state, ILocation path, Property showProperty, String propertyGetName, ITeamRepository repo, IndentingPrintStream printStream, IScmClientConfiguration config)
    throws FileSystemException
  {
    IVersionable versionable = null;
    try {
      versionable = (IVersionable)RepoUtil.getVersionables(Collections.singletonList(state), repo, config).get(0);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(NLS.bind(Messages.CheckInCmd_PATH_NOT_IN_REMOTE, path.toOSString()), e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    Map<String, String> properties = getInternalProperties(versionable);
    properties.putAll(versionable.getUserProperties());
    
    internalShowProperties(showProperty, propertyGetName, printStream, 
      properties);
  }
  

  private void internalShowProperties(Property showProperty, String propertyGetName, IndentingPrintStream printStream, Map<String, String> properties)
    throws CLIFileSystemClientException
  {
    if (config.isJSONEnabled()) {
      JSONObject props = JSONPrintUtil.jsonizeProperties(properties, showProperty, propertyGetName);
      config.getContext().stdout().print(props);
    }
    else if (showProperty == Property.NAME_VALUE) {
      if (properties.keySet().contains(propertyGetName)) {
        printStream.println(NLS.bind(Messages.PropertyListCmd_KeyValue, propertyGetName, properties.get(propertyGetName)));
      } else {
        throw StatusHelper.propertiesUnavailable(NLS.bind(Messages.PropertyListCmd_CouldNotGetProperty, propertyGetName));
      }
    } else {
      int maxWidth = 0;
      for (String propertyValue : properties.keySet()) {
        maxWidth = Math.max(maxWidth, propertyValue.length());
      }
      TreeSet<String> propertyNames = new TreeSet(properties.keySet());
      for (String propertyName : propertyNames) {
        if (showProperty == Property.ALL) {
          printStream.println(NLS.bind(Messages.PropertyListCmd_KeyValue, StringUtil.pad(propertyName, maxWidth), properties.get(propertyName)));
        } else if (showProperty == Property.LIST_NAMES) {
          printStream.println(NLS.bind(Messages.PropertyListCmd_Key, propertyName));
        }
      }
    }
  }
  
  private Map<String, String> getInternalProperties(IVersionable versionable)
  {
    Map<String, String> properties = new HashMap();
    
    if ((versionable instanceof IFileItem)) {
      IFileItem fileItem = (IFileItem)versionable;
      properties.put("jazz.executable", Boolean.toString(fileItem.isExecutable()));
      properties.put("jazz.line-delimiter", fileItem.getContent().getLineDelimiter().toString());
      properties.put("jazz.mime", fileItem.getContentType());
      properties.put("jazz.encoding", fileItem.getContent().getCharacterEncoding());
    }
    
    return properties;
  }
  
  private static class ConflictResult {
    final ITeamRepository repo;
    final ConflictSyncDTO conflict;
    final String itemId;
    final String stateId;
    
    ConflictResult(ConflictSyncDTO conflict, ITeamRepository repo) {
      this.conflict = conflict;
      this.repo = repo;
      itemId = conflict.getVersionableItemId();
      stateId = conflict.getOriginalSelectedContributorVersionableStateId();
    }
    
    ConflictResult(ConflictSyncDTO conflict, ITeamRepository repo, String itemId, String stateId) {
      this.conflict = conflict;
      this.repo = repo;
      this.itemId = itemId;
      this.stateId = stateId;
    }
  }
  
  private static class LocalConflictResult {
    final ITeamRepository repo;
    final LocalConflictSyncDTO conflict;
    final String itemId;
    
    LocalConflictResult(LocalConflictSyncDTO conflict, ITeamRepository repo) {
      this.conflict = conflict;
      this.repo = repo;
      itemId = conflict.getVersionableItemId();
    }
  }
  
  private List<ConflictResult> findConflicts(List<ParmsWorkspace> wsList, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    List<ConflictResult> conflicts = new ArrayList();
    
    SyncViewDTO syncView = SubcommandUtil.getSyncView(wsList, false, client, config);
    Iterator localIterator2;
    for (Iterator localIterator1 = syncView.getWorkspaces().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      WorkspaceSyncDTO wsSync = (WorkspaceSyncDTO)localIterator1.next();
      localIterator2 = wsSync.getComponents().iterator(); continue;ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator2.next();
      Iterator localIterator4; for (Iterator localIterator3 = compSync.getUnresolved().iterator(); localIterator3.hasNext(); 
          localIterator4.hasNext())
      {
        UnresolvedFolderSyncDTO unresolvedSync = (UnresolvedFolderSyncDTO)localIterator3.next();
        localIterator4 = unresolvedSync.getConflicts().iterator(); continue;ConflictSyncDTO conflictSync = (ConflictSyncDTO)localIterator4.next();
        if (!conflictSync.isSetCommonAncestorVersionableStateId())
        {
          ChangeSyncDTO changeDTO = matchPath(conflictSync.getPathHint(), compSync.getOutgoingChangeSetsAfterBasis());
          if (changeDTO != null) {
            conflicts.add(new ConflictResult(conflictSync, 
              RepoUtil.getSharedRepository(wsSync.getRepositoryUrl(), false), 
              changeDTO.getVersionableItemId(), changeDTO.getAfterStateId()));
          } else {
            throw StatusHelper.internalError(
              NLS.bind(Messages.ConflictsCmd_ConflictItem_NotFound, conflictSync.getPathHint()));
          }
        } else {
          conflicts.add(new ConflictResult(conflictSync, RepoUtil.getSharedRepository(wsSync.getRepositoryUrl(), false)));
        }
      }
    }
    


    return conflicts;
  }
  
  private List<LocalConflictResult> findLocalConflicts(List<ParmsWorkspace> wsList, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    List<LocalConflictResult> conflicts = new ArrayList();
    
    SyncViewDTO syncView = SubcommandUtil.getSyncView(wsList, false, client, config);
    Iterator localIterator2;
    for (Iterator localIterator1 = syncView.getWorkspaces().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      WorkspaceSyncDTO wsSync = (WorkspaceSyncDTO)localIterator1.next();
      localIterator2 = wsSync.getComponents().iterator(); continue;ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator2.next();
      Iterator localIterator4; for (Iterator localIterator3 = compSync.getUnresolved().iterator(); localIterator3.hasNext(); 
          localIterator4.hasNext())
      {
        UnresolvedFolderSyncDTO unresolvedSync = (UnresolvedFolderSyncDTO)localIterator3.next();
        localIterator4 = unresolvedSync.getLocalConflicts().iterator(); continue;LocalConflictSyncDTO conflictSync = (LocalConflictSyncDTO)localIterator4.next();
        conflicts.add(new LocalConflictResult(conflictSync, RepoUtil.getSharedRepository(wsSync.getRepositoryUrl(), false)));
      }
    }
    


    return conflicts;
  }
  



  private static ConflictResult findConflict(List<ConflictResult> conflicts, IUuidAliasRegistry.IUuidAlias conflictAlias, ResourcePropertiesDTO resProp, IScmClientConfiguration config)
  {
    if ((conflictAlias == null) && (resProp == null)) {
      return null;
    }
    
    for (ConflictResult result : conflicts) {
      if (((conflictAlias != null) && (conflictAlias.getUuid().getUuidValue().equals(itemId))) || (
        (resProp != null) && (resProp.getItemId().equals(itemId)))) {
        return result;
      }
    }
    
    return null;
  }
  



  private static LocalConflictResult findLocalConflict(List<LocalConflictResult> conflicts, IUuidAliasRegistry.IUuidAlias conflictAlias, ResourcePropertiesDTO resProp, IScmClientConfiguration config)
  {
    if ((conflictAlias == null) && (resProp == null)) {
      return null;
    }
    
    for (LocalConflictResult result : conflicts) {
      if (((conflictAlias != null) && (conflictAlias.getUuid().getUuidValue().equals(itemId))) || (
        (resProp != null) && (resProp.getItemId().equals(itemId)))) {
        return result;
      }
    }
    
    return null;
  }
  
  public static ChangeSyncDTO matchPath(String conflictPath, List<ChangeSetSyncDTO> outgoingChangeSets) { Iterator localIterator2;
    for (Iterator localIterator1 = outgoingChangeSets.iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      ChangeSetSyncDTO csDTO = (ChangeSetSyncDTO)localIterator1.next();
      localIterator2 = csDTO.getChanges().iterator(); continue;ChangeFolderSyncDTO changeFolderDTO = (ChangeFolderSyncDTO)localIterator2.next();
      for (ChangeSyncDTO changeDTO : changeFolderDTO.getChanges()) {
        String pathToCompare = changeDTO.getPathHint();
        if (changeDTO.isMoveType()) {
          pathToCompare = changeDTO.getNewPathHint();
        }
        
        if (pathToCompare.equalsIgnoreCase(conflictPath)) {
          return changeDTO;
        }
      }
    }
    

    return null;
  }
}
