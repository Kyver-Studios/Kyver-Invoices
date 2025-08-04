# Invoice System Redesign Plan

## Overview
This plan outlines the complete redesign of the invoice system to implement a channel-based workflow with DM notifications and payment gateway selection.

## Current System Analysis
- Basic invoice creation with embeds
- Payment gateway interfaces (PayPal, Stripe) 
- Webhook handling for payment notifications
- Database storage with SQLite
- Configuration management

## New Workflow Requirements

### 1. Invoice Creation Flow
1. User runs `/invoice create` command
2. System creates dedicated channel: `invoice-{invoiceId}-{username}`
3. Channel is created under invoice category from config
4. Base embed sent to channel with admin controls
5. DM sent to user with payment method selection

### 2. Channel Management
- **Channel Name Format**: `invoice-{short-id}-{username}`
- **Category**: Defined in config.yml (`invoice-category`)
- **Permissions**: User can view, admin can manage
- **Base Embed Buttons**:
  - Resend DM
  - Refresh Status  
  - Cancel

### 3. DM Payment Selection
- **Initial DM**: Embed with dropdown for payment methods
- **Options**: Only enabled gateways from config
- **Selection**: Deletes message, creates payment

### 4. Payment Processing
- Generate payment link via selected gateway
- Create QR code from payment URL
- Send "Payment Ready" embed with:
  - QR code as image
  - Title: "Payment Ready - {payment method}"
  - Description with amount/details
  - Buttons: "Pay Now", "Need Help", "Cancel Payment"

### 5. Payment States & Notifications
- **Payment Completed**: Update embeds in both DM and channel
- **Payment Cancelled**: Notify admins with action buttons
- **Payment Failed**: Handle errors gracefully

## Implementation Plan

### Phase 1: Core Infrastructure Updates
1. **Update Invoice Model**
   - Add channel ID storage
   - Add message ID tracking for embeds
   - Add QR code data field

2. **Create Channel Service**
   - Channel creation/management
   - Permission handling
   - Category integration

3. **Enhance EmbedManager**
   - Payment selection dropdown
   - Payment ready embeds
   - Status update embeds
   - Admin notification embeds

### Phase 2: Payment Gateway Enhancements
1. **QR Code Generation**
   - Add QR code library dependency
   - Implement QR code service
   - Generate codes from payment URLs

2. **Gateway URL Generation**
   - Ensure all gateways return proper payment URLs
   - Standardize URL format for QR codes

### Phase 3: User Interface Components
1. **Button/Dropdown Handlers**
   - Payment method selection
   - Admin control buttons
   - User action buttons

2. **DM Management Service**
   - Send initial payment selection
   - Update payment status
   - Handle cancellations

### Phase 4: Webhook & Status Management
1. **Enhanced Webhook Processing**
   - Update channel embeds on payment
   - Send admin notifications
   - Update user DMs

2. **Status Synchronization**
   - Real-time status updates
   - Cross-platform consistency

## Technical Requirements

### Dependencies to Add
- QR Code generation library (com.google.zxing)
- Enhanced JDA components for dropdowns/buttons

### Database Schema Updates
- Add channel_id to invoices table
- Add message tracking fields
- Add QR code data storage

### Configuration Updates
- Invoice category ID
- Admin role ID for notifications
- Enhanced gateway settings

### New Classes to Create
1. `ChannelService` - Channel management
2. `QRCodeService` - QR code generation
3. `DMService` - Direct message handling
4. `ComponentHandler` - Button/dropdown interactions
5. `NotificationService` - Admin/user notifications

### Enhanced Classes
1. `InvoiceCommand` - Updated creation flow
2. `EmbedManager` - New embed types
3. `WebhookHandler` - Enhanced notifications
4. `PaymentManager` - Integration orchestration

## File Structure Changes
```
src/main/java/net/kyver/invoices/
├── service/
│   ├── ChannelService.java (NEW)
│   ├── QRCodeService.java (NEW)
│   ├── DMService.java (NEW)
│   └── NotificationService.java (NEW)
├── handler/
│   └── ComponentHandler.java (NEW)
├── command/
│   └── InvoiceCommand.java (ENHANCED)
├── manager/
│   ├── EmbedManager.java (ENHANCED)
│   └── PaymentManager.java (ENHANCED)
└── api/
    └── WebhookHandler.java (ENHANCED)
```

## Implementation Order
1. Update build.gradle.kts with QR code dependency
2. Create new service classes
3. Update Invoice model and database schema
4. Enhance EmbedManager with new embed types
5. Implement ComponentHandler for interactions
6. Update InvoiceCommand with new workflow
7. Enhance webhook processing
8. Test end-to-end workflow
9. Deploy and monitor

## Testing Strategy
- Unit tests for each service
- Integration tests for payment flows
- Manual testing of Discord interactions
- Webhook simulation testing
- QR code generation validation

## Rollback Plan
- Keep existing invoice creation as fallback
- Feature flag for new vs old system
- Database migration scripts with rollback
- Configuration backward compatibility
