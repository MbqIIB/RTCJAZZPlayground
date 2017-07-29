package com.ibm.team.filesystem.cli.client.internal.users;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributorRecord;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.client.internal.ScmContributorUtil;
import java.io.PrintStream;
import java.util.List;







public class ListUsersCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListUsersCmd() {}
  
  public static final NamedOptionDefinition OPT_NOUSERIDS = new NamedOptionDefinition("n", "noUserIds", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_NOUSERIDS, Messages.ListUsersCmd_NO_USER_IDS);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAnc(config, client);
    
    List list = null;
    try {
      list = ScmContributorUtil.fetchContributors(repo, cli.hasOption(OPT_NOUSERIDS), null);
    }
    catch (TeamRepositoryException localTeamRepositoryException) {
      throw StatusHelper.failure(Messages.ListUsersCmd_UNABLE_TO_LIST_USERS, null);
    }
    
    if (list != null) {
      IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
      
      JSONArray recs = jsonizeRecords(list, repo);
      
      if (config.isJSONEnabled()) {
        config.getContext().stdout().print(recs);
        return;
      }
      
      if (recs.size() == 0) {
        return;
      }
      
      for (Object obj : recs) {
        JSONObject cr = (JSONObject)obj;
        
        printStream.println(AliasUtil.selector((String)cr.get("name"), 
          UUID.valueOf((String)cr.get("uuid")), (String)cr.get("url"), RepoUtil.ItemType.CONTRIBUTOR) + 
          " " + (String)cr.get("mail"));
      }
    }
  }
  
  JSONArray jsonizeRecords(List list, ITeamRepository repo)
  {
    JSONArray recs = new JSONArray();
    
    for (Object obj : list) {
      IContributorRecord cr = (IContributorRecord)obj;
      
      JSONObject rec = JSONPrintUtil.jsonize(cr.getName(), cr.getItemId().getUuidValue(), repo.getRepositoryURI());
      
      rec.put("mail", cr.getEmailAddress());
      
      recs.add(rec);
    }
    
    return recs;
  }
}
