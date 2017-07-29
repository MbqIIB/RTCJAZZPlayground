package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.workitem.common.model.IDeliverable;
import java.io.PrintStream;
import java.util.List;
import org.eclipse.osgi.util.NLS;









public class ListReleaseCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final PositionalOptionDefinition OPT_SNAPSHOT = new PositionalOptionDefinition("snapshot", 0, 1, "@");
  
  public ListReleaseCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_SNAPSHOT, Messages.AddReleaseToSnapshotCmd_SNAPSHOT_TO_ADD);
    
    return options;
  }
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT, null), config);
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    

    IBaselineSet ss = RepoUtil.getSnapshot(null, ssSelector.getItemSelector(), repo, config);
    

    JSONObject jSs = new JSONObject();
    jSs.put("name", ss.getName());
    
    jSs.put("uuid", ss.getItemId().getUuidValue());
    
    jSs.put("url", repo.getRepositoryURI());
    
    JSONArray jRel = getRelease(ss, repo, config);
    jSs.put("release", jRel);
    

    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jSs);
    } else {
      printProperties(jSs, config);
    }
  }
  
  private static JSONArray getRelease(IBaselineSet ss, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException {
    List<IDeliverable> fetched = RepoUtil.getBaselineSetReleases(ss, repo, config);
    JSONArray array = new JSONArray();
    for (IDeliverable iteration : fetched) {
      if (iteration != null) {
        JSONObject properties = new JSONObject();
        properties.put("uuid", iteration.getItemId().getUuidValue());
        properties.put("name", iteration.getName());
        properties.put("archived", Boolean.valueOf(iteration.isArchived()));
        array.add(properties);
      }
    }
    if (array.size() > 0) {
      return array;
    }
    return null;
  }
  
  private static void printProperties(JSONObject jSs, IScmClientConfiguration config) throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    String itemId = (String)jSs.get("uuid");
    String repoUri = (String)jSs.get("url");
    

    String name = (String)jSs.get("name");
    if (name != null) {
      name = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, RepoUtil.ItemType.SNAPSHOT);
      out.println(NLS.bind(Messages.WorkspacePropertiesCmd_NAME, name));
    }
    

    JSONArray jRelease = (JSONArray)jSs.get("release");
    if (jRelease != null) {
      out.println(Messages.SnapshotPropertiesCmd_RELEASES);
      for (Object entry : jRelease) {
        JSONObject prop = (JSONObject)entry;
        String release = (String)prop.get("name");
        Boolean archived = (Boolean)prop.get("archived");
        String output = release;
        if (archived.booleanValue()) {
          output = output + " (archived)";
        }
        out.indent().println(output);
      }
    }
  }
}
