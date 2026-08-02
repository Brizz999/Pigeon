package pigeon.notifiers.data;

import pigeon.message.Field;
import pigeon.util.Sanitizable;

import java.util.Collections;
import java.util.List;

public abstract class NotificationData implements Sanitizable {
    public List<Field> getFields() {
        return Collections.emptyList();
    }
}
