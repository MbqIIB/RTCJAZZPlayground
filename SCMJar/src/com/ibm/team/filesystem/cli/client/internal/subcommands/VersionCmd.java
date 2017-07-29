package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.internal.TeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.internal.util.ComponentConfigurationRegistry;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;
















public class VersionCmd
  extends com.ibm.team.rtc.cli.infrastructure.internal.subcommands.VersionCmd
{
  private static final NamedOptionDefinition OPT_SERVER_VERSION = new NamedOptionDefinition("s", "server", 0);
  
  private static final NamedOptionDefinition OPT_COMMAND_INFO = new NamedOptionDefinition("c", "commands", 0);
  private static final String FOUNDATION_COMPONENT_TAG = "com.ibm.team.jazz.foundation";
  private static final String RTC_COMPONENT_TAG = "com.ibm.team.rtc";
  
  public VersionCmd() {}
  
  public int run(IClientConfiguration config) throws CLIClientException
  {
    JSONObject root = new JSONObject();
    

    String clientBuildId = System.getProperty("scm.daemon.buildId", Messages.VersionCmd_MISSING_VALUE);
    root.put("client-build-id", clientBuildId);
    

    String clientVersion = getClientVersion();
    if ((clientVersion == null) || (clientVersion.length() == 0)) {
      clientVersion = Messages.VersionCmd_MISSING_VALUE;
    }
    root.put("client-version", clientVersion);
    
    ICommandLine cli = config.getSubcommandCommandLine();
    

    if ((cli.hasOption(OPT_SERVER_VERSION)) && (cli.hasOption(CommonOptions.OPT_URI))) {
      try {
        throw StatusHelper.argSyntax(NLS.bind(Messages.VersionCmd_MUTUALLY_EXCLUSIVE_OPTIONS, 
          cli.getDefinition().getOption(OPT_SERVER_VERSION).getName(), 
          cli.getDefinition().getOption(CommonOptions.OPT_URI).getName()));
      } catch (FileSystemException e) {
        throw StatusHelper.toCLIClientException(e);
      }
    }
    

    if (cli.hasOption(CommonOptions.OPT_URI)) {
      IScmClientConfiguration configScm = (IScmClientConfiguration)config;
      Map<String, Object> repoList = getRepository(configScm);
      
      for (Map.Entry<String, Object> entry : repoList.entrySet())
      {
        if ((entry.getKey() != null) && (entry.getValue() != null))
        {


          root.put("repository", getJsonOject(entry));
        }
      }
    }
    Object entry;
    if (cli.hasOption(OPT_SERVER_VERSION)) {
      IScmClientConfiguration configScm = (IScmClientConfiguration)config;
      
      try
      {
        File cfaRootPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
        if (cfaRootPath == null) {
          throw StatusHelper.argSyntax(Messages.VersionCmd_INVALID_SANDBOX);
        }
        repoList = getRepository(configScm);
      } catch (FileSystemException e) {
        Map<String, Object> repoList;
        throw StatusHelper.toCLIClientException(e);
      }
      Map<String, Object> repoList;
      JSONArray ja = new JSONArray();
      
      for (Iterator localIterator2 = repoList.entrySet().iterator(); localIterator2.hasNext();) { entry = (Map.Entry)localIterator2.next();
        
        if ((((Map.Entry)entry).getKey() != null) && (((Map.Entry)entry).getValue() != null))
        {


          ja.add(getJsonOject((Map.Entry)entry)); }
      }
      root.put("sandbox-repositories", ja);
    }
    

    if (cli.hasOption(OPT_COMMAND_INFO))
    {
      Map<Map<String, String>, String> cmdList = getCommands(config);
      JSONArray ja = new JSONArray();
      Iterator localIterator3;
      for (entry = cmdList.entrySet().iterator(); ((Iterator)entry).hasNext(); 
          

          localIterator3.hasNext())
      {
        Map.Entry<Map<String, String>, String> entry = (Map.Entry)((Iterator)entry).next();
        
        JSONObject jCmd = new JSONObject();
        localIterator3 = ((Map)entry.getKey()).entrySet().iterator(); continue;Map.Entry<String, String> entry1 = (Map.Entry)localIterator3.next();
        if ((entry1.getKey() != null) && (entry1.getValue() != null))
        {

          String namespace = ((String)entry1.getKey()).toString();
          String version = ((String)entry1.getValue()).toString();
          jCmd.put("namespace", namespace);
          jCmd.put("version", version);
          jCmd.put("sub-commands", ((String)entry.getValue()).toString());
          ja.add(jCmd);
        }
      }
      root.put("commands", ja);
    }
    
    if (cli.hasOption(CommonOptions.OPT_JSON))
    {
      config.getContext().stdout().println(root.toString());
    }
    else {
      printNonJson(config, root);
    }
    
    return 0;
  }
  





  private JSONObject getJsonOject(Map.Entry<String, Object> entry)
  {
    JSONObject jRepo = new JSONObject();
    if ((entry.getValue() instanceof ITeamRepository)) {
      TeamRepository repo = (TeamRepository)entry.getValue();
      jRepo = insertServerDetails(repo);
    }
    else {
      String errMessage = (String)entry.getValue();
      jRepo = insertErrServerDetails((String)entry.getKey(), errMessage);
    }
    return jRepo;
  }
  






  private JSONObject insertErrServerDetails(String uri, String message)
  {
    if ((uri == null) || (uri.length() == 0)) {
      uri = Messages.VersionCmd_MISSING_VALUE;
    }
    
    JSONObject jRepo = new JSONObject();
    jRepo.put("server-uuid", Messages.VersionCmd_UNKNOWN_VALUE);
    jRepo.put("server-uri", uri);
    jRepo.put("error-message", message);
    jRepo.put("server-build-id", Messages.VersionCmd_UNKNOWN_VALUE);
    jRepo.put("server-version", Messages.VersionCmd_UNKNOWN_VALUE);
    return jRepo;
  }
  




  private JSONObject insertServerDetails(TeamRepository repo)
  {
    String serverVersion = repo.getRepositoryVersion();
    if ((serverVersion == null) || (serverVersion.length() == 0)) {
      serverVersion = Messages.VersionCmd_MISSING_VALUE;
    }
    
    String buildId = repo.getRepositoryBuildId();
    if ((buildId == null) || (buildId.length() == 0)) {
      buildId = Messages.VersionCmd_MISSING_VALUE;
    }
    
    String uri = repo.getRepositoryURI();
    if ((uri == null) || (uri.length() == 0)) {
      uri = Messages.VersionCmd_MISSING_VALUE;
    }
    
    String uuid = repo.getId().getUuidValue();
    if ((uuid == null) || (uuid.length() == 0)) {
      uuid = Messages.VersionCmd_MISSING_VALUE;
    }
    
    JSONObject jRepo = new JSONObject();
    jRepo.put("server-uuid", uuid);
    jRepo.put("server-uri", uri);
    jRepo.put("server-build-id", buildId);
    jRepo.put("server-version", serverVersion);
    return jRepo;
  }
  





  private void printNonJson(IClientConfiguration config, JSONObject js)
  {
    config.getSubcommandCommandLine();
    
    String ClientBuildId = (String)js.get("client-build-id");
    config.getContext().stdout().println(NLS.bind(Messages.VersionCmd_BUILD_ID_HEADER, ClientBuildId));
    String ClientVersion = (String)js.get("client-version");
    config.getContext().stdout().println(NLS.bind(Messages.VersionCmd_VERSION, ClientVersion));
    
    JSONArray ja = (JSONArray)js.get("sandbox-repositories");
    if (ja != null) {
      for (int i = 0; i < ja.size(); i++) {
        JSONObject jo = (JSONObject)ja.get(i);
        printJsonObject(config, jo);
      }
    }
    
    JSONObject jo = (JSONObject)js.get("repository");
    if (jo != null) {
      printJsonObject(config, jo);
    }
    
    JSONArray ja1 = (JSONArray)js.get("commands");
    if (ja1 != null) {
      Map<Map<String, String>, String> list = new HashMap();
      
      for (int i = 0; i < ja1.size(); i++) {
        Map<String, String> key = new HashMap();
        JSONObject jo1 = (JSONObject)ja1.get(i);
        String namespace = (String)jo1.get("namespace");
        String version = (String)jo1.get("version");
        String subCommands = (String)jo1.get("sub-commands");
        key.put(namespace, version);
        list.put(key, subCommands);
      }
      config.getContext().stdout().println();
      displayCommands(config, list);
    }
  }
  




  private void printJsonObject(IClientConfiguration config, JSONObject jo)
  {
    config.getContext().stdout().println();
    config.getContext().stdout().println(NLS.bind(Messages.VersionCmd_SERVER_URI, (String)jo.get("server-uri")));
    config.getContext().stdout().println(NLS.bind(Messages.VersionCmd_SERVER_BUILD_ID, (String)jo.get("server-build-id")));
    config.getContext().stdout().println(NLS.bind(Messages.VersionCmd_SERVER_VERSION, (String)jo.get("server-version")));
    if (jo.get("error-message") != null) {
      config.getContext().stdout().println(NLS.bind(Messages.VersionCmd_Error_MESSAGE, (String)jo.get("error-message")));
    }
  }
  
  public Options getOptions() throws ConflictingOptionException
  {
    Options options = super.getOptions();
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(OPT_SERVER_VERSION, Messages.VersionCmd_OPT_SERVER_VERSION_HELP, false)
      .addOption(OPT_COMMAND_INFO, Messages.VersionCmd_OPT_COMMAND_INFO_HELP, false)
      .addOption(CommonOptions.OPT_JSON, Messages.VersionCmd_OPT_JSON_HELP, false)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP, false);
    return options;
  }
  



  public String getClientVersion()
  {
    Map<String, String> compatibilityMap = ComponentConfigurationRegistry.INSTANCE.getClientCompatibilityMap();
    String version = (String)compatibilityMap.get("com.ibm.team.rtc");
    if (version == null)
    {




      version = (String)compatibilityMap.get("com.ibm.team.jazz.foundation");
    }
    return version;
  }
  




  public static Map<String, Object> getRepository(IScmClientConfiguration config)
  {
    IFilesystemRestClient client = null;
    Map<String, Object> repo = new HashMap();
    try
    {
      client = SubcommandUtil.setupDaemon(config);
      repo = RepoUtil.loginUrlArgAncs(config, client);
    } catch (FileSystemException e) {
      config.getContext().stdout().println(e.getMessage());
    }
    return repo;
  }
}
