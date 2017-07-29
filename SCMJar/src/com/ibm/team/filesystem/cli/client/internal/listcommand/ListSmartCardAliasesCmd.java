package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.repository.client.login.SmartCardLoginInfo;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.io.PrintStream;
import java.util.List;



public class ListSmartCardAliasesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListSmartCardAliasesCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options opts = new Options(false, true);
    
    opts.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    opts.addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP);
    return opts;
  }
  
  public void run() throws FileSystemException
  {
    if (!Constants.ON_WINDOWS) {
      throw StatusHelper.disallowed(Messages.ListSmartCardAliases_NOT_SUPPORTED);
    }
    
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    SmartCardLoginInfo loginInfo = new SmartCardLoginInfo();
    List<String> aliases = loginInfo.getAliases();
    
    JSONArray jAliases = jsonizeAliases(aliases);
    
    if (config.isJSONEnabled()) {
      JSONObject jResult = new JSONObject();
      jResult.put("aliases", jAliases);
      config.getContext().stdout().print(jResult);
      return;
    }
    
    if (aliases.size() == 0) {
      config.getContext().stdout().println(Messages.ListSmartCardAliases_NOT_FOUND);
      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        config.getContext().stdout().println(Messages.ListSmartCardAliases_NOT_FOUND_HINT);
      }
      return;
    }
    
    for (String alias : aliases) {
      config.getContext().stdout().println(alias);
    }
  }
  
  private JSONArray jsonizeAliases(List<String> aliases) {
    JSONArray jAliases = new JSONArray();
    for (String alias : aliases) {
      JSONObject jAlias = new JSONObject();
      jAlias.put("alias", alias);
    }
    
    return jAliases;
  }
}
