This project is an experimental playground to try implementing an application with only Jetpack Compose without using the old Android UI framework.
This application will only have the minimal feature support (Catalog/Thread viewing, posting, bookmarks, album, media viewer, some other stuff). 
No advansed stuff like archives, sound posts, thirdeye, anti-captcha services etc.

From the technology perspective, this project uses a lot of home-made compose stuff:
- Compose navigation (Normal screens/dialog screen/screen transitions).
- PullToRefresh.
- Toolbar (with ability to switch between different toolbar types like normal toolbar -> search toolbar). Toolbar auto hides/reveals itself depending on a number of factors (like when scroll lazyColumn down).
- Drawer.
- Snackbars (toasts show up as snackbars too).
- LazyColumn with scrollbar and fast scroller (drag the scrollbar thumb to scroll).
- Two layout types: normal and split. Can be locked into either of them or set to be applied automatically (when using tablet or when switching to album orientation).
- A lot of custom gesture handling (swipe bottom left corner to right to pull the drawer on any pager screen, pull left when on the 0th screen to pull the drawer).

If you are interested in advanced compose stuff then you may find it interesting.

One of the main goals of the app is to make catalog/thread loading as fast as possible by utilizing asynchronous post parsing. In KurobaEx all posts are fully parsed before being displayed (reply chains/filters/etc are processed before the posts are displayed as well) which makes catalog/thread loading very slow. In KurobaExLite, however, we only initially parse a very small window of posts (16-32) and the rest is parsed asynchronously while you navigate the catalog/thread. This makes catalog/thread loading instantaneous (Threads with ~1k posts are displayed in less than a second).

There is not much to see yet, but if you are really interested you will have to build the project yourself (You will need to generate the keys to sign a release build. Then run gradlew assembleStableRelease). Expect bugs, obviously. 

Right now you can:
- Visit any 4chan board.
- Navigate reply chains. 
- Use search.
- Thread scroll position is stored/restored.

What you can't do which will be implemented in the future:
- Reply to posts.
- View media.
- Navigate other sites.
- Open thread albums.

The Main work is currently being done on the general UI/UX which is going to be different from KurobaEx.
