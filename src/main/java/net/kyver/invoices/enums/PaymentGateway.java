package net.kyver.invoices.enums;

import net.kyver.invoices.manager.ConfigManager;

public enum PaymentGateway {
    STRIPE("stripe", "Stripe", "Credit Cards, Digital Wallets", "https://stripe.com/img/v3/newsroom/social.png", "💳"),
    PAYPAL("paypal", "PayPal", "PayPal Balance, Credit Cards", "https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_111x69.jpg", "🅿️"),
    BRAINTREE("braintree", "Braintree", "Credit Cards, PayPal, Venmo", "https://www.braintreepayments.com/images/braintree-logo-black.png", "💰"),
    ADYEN("adyen", "Adyen", "Global Payment Methods", "https://www.adyen.com/images/logos/adyen-logo-green.svg", "🌍"),
    SQUARE("square", "Square", "Credit Cards, Digital Wallets", "https://squareup.com/images/brand/downloads/Square-Logo.png", "□"),
    RAZORPAY("razorpay", "Razorpay", "UPI, Cards, Wallets", "https://razorpay.com/assets/razorpay-logo.svg", "⚡"),
    MOLLIE("mollie", "Mollie", "European Payment Methods", "https://www.mollie.com/images/logo/mollie-logo.svg", "🇪🇺"),
    COINBASE("coinbase", "Coinbase Commerce", "Cryptocurrency Payments", "https://coinbase.com/img/favicon.ico", "₿"),
    WEPAY("wepay", "WePay", "Integrated Payments", "https://wepay.com/img/logo.png", "🤝"),
    AUTHORIZE_NET("authorize_net", "Authorize.Net", "Credit Card Processing", "https://www.authorize.net/images/authorize-net-logo.png", "✔️");

    private final String id;
    private final String displayName;
    private final String description;
    private final String iconUrl;
    private final String emoji;

    PaymentGateway(String id, String displayName, String description, String iconUrl, String emoji) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.iconUrl = iconUrl;
        this.emoji = emoji;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getFormattedName() {
        return emoji + " " + displayName;
    }

    public static PaymentGateway fromId(String id) {
        for (PaymentGateway gateway : values()) {
            if (gateway.getId().equals(id)) {
                return gateway;
            }
        }
        return null;
    }

    public boolean isEnabled() {
        ConfigManager config = ConfigManager.getInstance();
        return switch (this) {
            case STRIPE -> config.isStripeEnabled();
            case PAYPAL -> config.isPayPalEnabled();
            case BRAINTREE -> false;
            case ADYEN -> false;
            case SQUARE -> false;
            case RAZORPAY -> false;
            case MOLLIE -> false;
            case COINBASE -> false;
            case WEPAY -> false;
            case AUTHORIZE_NET -> false;
        };
    }
}
