.class Lopenlight/co/camera/BasePreviewFragment$6;
.super Lopenlight/co/touchstrip/TouchStrip$OnTouchStripEventListener;
.source "BasePreviewFragment.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lopenlight/co/camera/BasePreviewFragment;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lopenlight/co/camera/BasePreviewFragment;


# direct methods
.method constructor <init>(Lopenlight/co/camera/BasePreviewFragment;)V
    .registers 2

    .line 724
    iput-object p1, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    invoke-direct {p0}, Lopenlight/co/touchstrip/TouchStrip$OnTouchStripEventListener;-><init>()V

    return-void
.end method


.method private stepZoom(Lopenlight/co/camera/BasePreviewFragment;F)V
    .registers 5

    # Surface the zoom wheel the same way a display scroll does, then advance the
    # zoom by exactly one detent in the direction of p2.
    sget-object v0, Lopenlight/co/camera/view/zoom/ZoomWheel$EventType;->TOUCHSTRIP:Lopenlight/co/camera/view/zoom/ZoomWheel$EventType;

    invoke-virtual {p1, v0}, Lopenlight/co/camera/BasePreviewFragment;->showZoomWheelView(Lopenlight/co/camera/view/zoom/ZoomWheel$EventType;)V

    invoke-virtual {p1}, Lopenlight/co/camera/BasePreviewFragment;->removeFocusCrossHair()V

    iget-object v0, p1, Lopenlight/co/camera/BasePreviewFragment;->mZoomManager:Lopenlight/co/camera/managers/zoom/ZoomManager;

    invoke-virtual {v0, p2}, Lopenlight/co/camera/managers/zoom/ZoomManager;->setZoom(F)V

    iget-object v0, p1, Lopenlight/co/camera/BasePreviewFragment;->mZoomManager:Lopenlight/co/camera/managers/zoom/ZoomManager;

    iget-object v1, p1, Lopenlight/co/camera/BasePreviewFragment;->mZoomWheel:Lopenlight/co/camera/view/zoom/ZoomWheel;

    invoke-virtual {v0, v1}, Lopenlight/co/camera/managers/zoom/ZoomManager;->performZoom(Lopenlight/co/camera/view/zoom/ZoomWheel;)V

    return-void
.end method


# virtual methods
.method public onEvent(Lopenlight/co/touchstrip/TouchStrip$Event;)V
    .registers 5

    .line 728
    # getter for: Lopenlight/co/camera/BasePreviewFragment;->TAG:Ljava/lang/String;
    invoke-static {}, Lopenlight/co/camera/BasePreviewFragment;->access$000()Ljava/lang/String;

    move-result-object v0

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "Touch Strip Event: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Lopenlight/co/touchstrip/TouchStrip$Event;->name()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lopenlight/co/lib/utils/LogUtil;->v(Ljava/lang/String;Ljava/lang/String;)V

    .line 729
    iget-object v0, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    iget-object v0, v0, Lopenlight/co/camera/BasePreviewFragment;->mCameraManager:Lopenlight/co/camera/utils/Provider;

    invoke-interface {v0}, Lopenlight/co/camera/utils/Provider;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lopenlight/co/camera/managers/CameraManager;

    invoke-virtual {v0}, Lopenlight/co/camera/managers/CameraManager;->isIdle()Z

    move-result v0

    if-eqz v0, :cond_42

    .line 730
    sget-object v0, Lopenlight/co/touchstrip/TouchStrip$Event;->FLING_RIGHT:Lopenlight/co/touchstrip/TouchStrip$Event;

    if-ne p1, v0, :cond_37

    .line 731
    iget-object p0, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    const/4 p1, 0x1

    # invokes: Lopenlight/co/camera/BasePreviewFragment;->setZoomToNextPrimeLevel(Z)V
    invoke-static {p0, p1}, Lopenlight/co/camera/BasePreviewFragment;->access$400(Lopenlight/co/camera/BasePreviewFragment;Z)V

    goto :goto_4b

    .line 732
    :cond_37
    sget-object v0, Lopenlight/co/touchstrip/TouchStrip$Event;->FLING_LEFT:Lopenlight/co/touchstrip/TouchStrip$Event;

    if-ne p1, v0, :cond_tap_right

    .line 733
    iget-object p0, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    const/4 p1, 0x0

    # invokes: Lopenlight/co/camera/BasePreviewFragment;->setZoomToNextPrimeLevel(Z)V
    invoke-static {p0, p1}, Lopenlight/co/camera/BasePreviewFragment;->access$400(Lopenlight/co/camera/BasePreviewFragment;Z)V

    goto :goto_4b

    # A tap jumps between prime focal lengths, matching the fling behaviour.
    :cond_tap_right
    sget-object v0, Lopenlight/co/touchstrip/TouchStrip$Event;->TAP_RIGHT:Lopenlight/co/touchstrip/TouchStrip$Event;

    if-ne p1, v0, :cond_tap_left

    iget-object p0, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    const/4 p1, 0x1

    invoke-static {p0, p1}, Lopenlight/co/camera/BasePreviewFragment;->access$400(Lopenlight/co/camera/BasePreviewFragment;Z)V

    goto :goto_4b

    :cond_tap_left
    sget-object v0, Lopenlight/co/touchstrip/TouchStrip$Event;->TAP_LEFT:Lopenlight/co/touchstrip/TouchStrip$Event;

    if-ne p1, v0, :cond_swipe_right

    iget-object p0, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    const/4 p1, 0x0

    invoke-static {p0, p1}, Lopenlight/co/camera/BasePreviewFragment;->access$400(Lopenlight/co/camera/BasePreviewFragment;Z)V

    goto :goto_4b

    # A swipe steps the zoom continuously through every wheel detent instead of
    # snapping to a prime, so the strip scrubs the focal range smoothly.
    :cond_swipe_right
    sget-object v0, Lopenlight/co/touchstrip/TouchStrip$Event;->SWIPE_RIGHT:Lopenlight/co/touchstrip/TouchStrip$Event;

    if-ne p1, v0, :cond_swipe_left

    iget-object v0, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    const/high16 v1, 0x3f800000    # 1.0f

    invoke-direct {p0, v0, v1}, Lopenlight/co/camera/BasePreviewFragment$6;->stepZoom(Lopenlight/co/camera/BasePreviewFragment;F)V

    goto :goto_4b

    :cond_swipe_left
    sget-object v0, Lopenlight/co/touchstrip/TouchStrip$Event;->SWIPE_LEFT:Lopenlight/co/touchstrip/TouchStrip$Event;

    if-ne p1, v0, :cond_4b

    iget-object v0, p0, Lopenlight/co/camera/BasePreviewFragment$6;->this$0:Lopenlight/co/camera/BasePreviewFragment;

    const/high16 v1, -0x40800000    # -1.0f

    invoke-direct {p0, v0, v1}, Lopenlight/co/camera/BasePreviewFragment$6;->stepZoom(Lopenlight/co/camera/BasePreviewFragment;F)V

    goto :goto_4b

    .line 738
    :cond_42
    # getter for: Lopenlight/co/camera/BasePreviewFragment;->TAG:Ljava/lang/String;
    invoke-static {}, Lopenlight/co/camera/BasePreviewFragment;->access$000()Ljava/lang/String;

    move-result-object p0

    const-string p1, "Ignoring Touch Strip Event, the camera is not idle (meter/capture/focus in progress)"

    invoke-static {p0, p1}, Lopenlight/co/lib/utils/LogUtil;->v(Ljava/lang/String;Ljava/lang/String;)V

    :cond_4b
    :goto_4b
    return-void
.end method
