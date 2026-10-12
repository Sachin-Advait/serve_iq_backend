package com.gis.servelq.models;

/**
 * Why an agent moved a token to another counter. The list follows the queue
 * procedure (section 9.g); the agent app shows it as radio buttons.
 */
public enum TransferReason {
    WRONG_SERVICE("Visitor selected the wrong service"),
    NEEDS_SPECIALIST("Needs a specialist officer"),
    MISSING_DOCUMENTS("Required documents are missing"),
    OTHER_SECTION("Needs to consult another section"),
    MISCLASSIFIED("Service was classified incorrectly at ticket issue"),
    ALTERNATIVE_CHANNEL("Directed to an online or Contact Centre channel"),
    CROWD_MANAGEMENT("Crowd management"),
    OTHER("Other");

    private final String label;

    TransferReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
