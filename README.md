This project is an experimental playground to try implementing an application with only Jetpack Compose without using the old Android UI framework.
This application will only have the minimal feature support (Catalog/Thread viewing, posting, bookmarks, album, media viewer, some other stuff). 
No advansed stuff like archives, sound posts, thirdeye, anti-captcha services etc.

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
