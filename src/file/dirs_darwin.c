/*
 * This file is part of libbluray
 * Copyright (C) 2012   Konstantin Pavlov
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see
 * <http://www.gnu.org/licenses/>.
 */

#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include "dirs.h"

#include <CoreFoundation/CoreFoundation.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "util/strutl.h"
#include "util/logging.h"

#define USER_CFG_DIR   "Library/Preferences"
#define USER_CACHE_DIR "Library/Caches"
#define USER_DATA_DIR  "Library"
#define SYSTEM_CFG_DIR "/Library/Preferences"


const char *file_get_config_home(void)
{
    static char *dir       = NULL;
    static int   init_done = 0;

    if (!init_done) {
        init_done = 1;

        const char *user_home = getenv("HOME");
        if (user_home && *user_home) {
            return dir = str_printf("%s/%s", user_home, USER_CFG_DIR);
        }

        BD_DEBUG(DBG_FILE, "Can't find user home directory ($HOME) !\n");
    }

    return dir;
}

const char *file_get_data_home(void)
{
    static char *dir       = NULL;
    static int   init_done = 0;

    if (!init_done) {
        init_done = 1;

        const char *user_home = getenv("HOME");
        if (user_home && *user_home) {
            return dir = str_printf("%s/%s", user_home, USER_DATA_DIR);
        }

        BD_DEBUG(DBG_FILE, "Can't find user home directory ($HOME) !\n");
    }

    return dir;
}

const char *file_get_cache_home(void)
{
    static char *dir       = NULL;
    static int   init_done = 0;

    if (!init_done) {
        init_done = 1;

        const char *user_home = getenv("HOME");
        if (user_home && *user_home) {
            return dir = str_printf("%s/%s", user_home, USER_CACHE_DIR);
        }

        BD_DEBUG(DBG_FILE, "Can't find user home directory ($HOME) !\n");
    }

    return dir;
}

const char *file_get_config_system(const char *dir)
{
    static char *dirs = NULL; // "dir1\0dir2\0...\0dirN\0\0"

    if (!dirs) {
        dirs = str_printf("%s%c%c", SYSTEM_CFG_DIR, 0, 0);
    }

    if (!dir) {
        // first call
        dir = dirs;
    } else {
        // next call
        dir += strlen(dir) + 1;
        if (!*dir) {
            // end of list
            dir = NULL;
        }
    }

    return dir;
}
