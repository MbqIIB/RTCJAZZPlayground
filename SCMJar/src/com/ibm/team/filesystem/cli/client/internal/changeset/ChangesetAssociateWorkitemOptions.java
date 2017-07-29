package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;









public class ChangesetAssociateWorkitemOptions
  implements IOptionSource
{
  public static final IPositionalOptionDefinition OPT_WORKITEM = new PositionalOptionDefinition("work-item-number", 1, 1, "@");
  
  public ChangesetAssociateWorkitemOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options opts = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(opts);
    
    opts.addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetAssociateWorkitemOptions_1);
    opts.addOption(ChangesetCommonOptions.OPT_CHANGESET, ChangesetCommonOptions.OPT_CHANGESET_HELP);
    opts.addOption(OPT_WORKITEM, Messages.ChangesetAssociateWorkitemOptions_0);
    
    return opts;
  }
}
