#!/bin/bash
#grep -RIl "\<string name=\"tutorial_detailslist" . | xargs sed -i -e '/string name="msg_select/a \
#\ \ \ \ <string name="msg_submit">You cannot submit restrictions for more than three applications at a time</string>'    

#grep -RIl "\<string name=\"msg_edit" . | xargs sed -i -e '/msg_edit/d'
#grep -RIl "\<string name=\"settings_experimental" . | xargs sed -i -e 's/Experimental features/Experimental functions/g'


grep -RIl "\<string name=\"tutorial_detailslist" . | xargs sed -i -e 's/You must select some applications first/Please select some applications/g'
grep -RIl "\<string name=\"tutorial_detailslist" . | xargs sed -i -e 's/msg_limit">Please select some applications/msg_limit">Please select fewer than %d applications/g'
