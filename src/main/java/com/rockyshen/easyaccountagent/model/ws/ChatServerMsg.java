package com.rockyshen.easyaccountagent.model.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatServerMsg {
    /** connected / message_delta / message_end / error */
    private String type;
    private String content;
    private String message;
}
