package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.util.IRepositoryRecord;
import com.ibm.team.filesystem.client.util.IRepositoryRegistry;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.io.PrintStream;
import org.eclipse.osgi.util.NLS;

public class ListCredentialsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListCredentialsCmd() {}
  
  public void run()
    throws FileSystemException
  {
    IRepositoryRegistry repoReg = config.getRepositoryRegistry();
    
    JSONArray creds = jsonizeCreds(repoReg);
    
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(creds);
      return;
    }
    
    if (creds.size() == 0) {
      return;
    }
    
    if (config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_VERBOSE)) {
      config.getContext().stdout().println(Messages.ListRepositoriesCmd_0);
    }
    
    for (Object obj : creds)
    {
      JSONObject rec = (JSONObject)obj;
      
      String userName = (String)rec.get("userName");
      
      String nick = (String)rec.get("repoNickName");
      
      String password = (String)rec.get("password");
      
      String certLoc = (String)rec.get("certLoc");
      
      String smartCard = (String)rec.get("smartCard");
      
      String kerberos = (String)rec.get("kerberos");
      
      config.getContext().stdout().println(NLS.bind(
        Messages.ListRepositoriesCmd_5, new Object[] {
        rec.get("url"), userName, nick, certLoc, smartCard, kerberos, password }));
    }
  }
  


  JSONArray jsonizeCreds(IRepositoryRegistry repoReg)
  {
    JSONArray creds = new JSONArray();
    
    for (IRepositoryRecord rec : repoReg)
    {
      JSONObject cred = new JSONObject();
      
      String userName = rec.getUsername();
      
      if (userName == null) {
        userName = Messages.ListRepositoriesCmd_1;
      }
      
      cred.put("userName", userName);
      
      String nick = rec.getNickname();
      if (nick == null) {
        nick = Messages.ListRepositoriesCmd_2;
      }
      
      cred.put("repoNickName", nick);
      
      String password = Messages.ListRepositoriesCmd_3;
      if (rec.getPassword() == null) {
        password = Messages.ListRepositoriesCmd_4;
      }
      
      cred.put("password", password);
      
      String certLoc = rec.getCertficiateLocation();
      if (certLoc == null) {
        certLoc = Messages.ListRepositoriesCmd_NoCertificate;
      }
      
      cred.put("certLoc", certLoc);
      
      String smartCard = Messages.ListRepositoriesCmd_UseSmartCard;
      if (!rec.isSmartCard()) {
        smartCard = Messages.ListRepositoriesCmd_NoSmartCard;
      }
      
      cred.put("smartCard", smartCard);
      String kerberos;
      String kerberos;
      if (rec.isKerberos()) {
        kerberos = Messages.ListCredentialsCmd_0;
      } else {
        kerberos = Messages.ListCredentialsCmd_1;
      }
      cred.put("kerberos", kerberos);
      
      cred.put("url", rec.getUrl());
      
      if (rec.getUserId() != null) {
        cred.put("user-id", rec.getUserId().getUuidValue());
      }
      
      creds.add(cred);
    }
    
    return creds;
  }
  
  public Options getOptions() throws ConflictingOptionException {
    Options opts = new Options(false, true);
    
    opts.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.ListRepositoriesCmd_6);
    

    return opts;
  }
}
