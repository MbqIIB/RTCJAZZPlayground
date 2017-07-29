package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;












public class ListCmdContribOptions
  extends ListCmdOptions
{
  public static final IOptionKey OPT_CONTRIB = new OptionKey("contrib");
  
  public ListCmdContribOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { return super.getOptions().addOption(OPT_CONTRIB, "c", "contrib", Messages.ListCmdOptions_0, 1); }
}
