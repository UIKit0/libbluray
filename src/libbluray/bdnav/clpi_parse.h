/*
 * This file is part of libbluray
 * Copyright (C) 2009-2010  John Stebbins
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * In addition, as a special exception, the copyright holders of libbluray
 * gives permission to link the code of its release of libbluray with the
 * OpenSSL project's "OpenSSL" library (or with modified versions of it
 * that use the same license as the "OpenSSL" library), and distribute
 * the linked executables.  You must obey the GNU General Public License
 * in all respects for all of the code used other than "OpenSSL".  If you
 * modify this file, you may extend this exception to your version of the
 * file, but you are not obligated to do so.  If you do not wish to do
 * so, delete this exception statement from your version.
 */

#if !defined(_CLPI_PARSE_H_)
#define _CLPI_PARSE_H_

typedef struct
{
    uint16_t          pcr_pid;
    uint32_t          spn_stc_start;
    uint32_t          presentation_start_time;
    uint32_t          presentation_end_time;
} CLPI_STC_SEQ;

typedef struct
{
    uint32_t          spn_atc_start;
    uint8_t           num_stc_seq;
    uint8_t           offset_stc_id;
    CLPI_STC_SEQ     *stc_seq;
} CLPI_ATC_SEQ;

typedef struct
{
    uint8_t           num_atc_seq;
    CLPI_ATC_SEQ     *atc_seq;
} CLPI_SEQ_INFO;

typedef struct
{
    uint8_t           validity;
    uint8_t           format_id[5];
} CLPI_TS_TYPE;

typedef struct
{
    uint32_t          delta;
    uint8_t           file_id[6];
    uint8_t           file_code[5];
} CLPI_ATC_DELTA;

typedef struct
{
    uint8_t           clip_stream_type;
    uint8_t           application_type;
    uint8_t           is_atc_delta;
    uint32_t          ts_recording_rate;
    uint32_t          num_source_packets;
    CLPI_TS_TYPE      ts_type_info;
    uint8_t           atc_delta_count;
    CLPI_ATC_DELTA   *atc_delta;
} CLPI_CLIP_INFO;

typedef struct
{
    uint16_t          pid;
    uint8_t           coding_type;
    uint8_t           format;
    uint8_t           rate;
    uint8_t           aspect;
    uint8_t           oc_flag;
    uint8_t           char_code;
    uint8_t           lang[4];
} CLPI_PROG_STREAM;

typedef struct
{
    uint32_t          spn_program_sequence_start;
    uint16_t          program_map_pid;
    uint8_t           num_streams;
    uint8_t           num_groups;
    CLPI_PROG_STREAM *streams;
} CLPI_PROG;

typedef struct
{
    uint8_t           num_prog;
    CLPI_PROG        *progs;
} CLPI_PROG_INFO;

typedef struct
{
    int               ref_ep_fine_id;
    int               pts_ep;
    uint32_t          spn_ep;
} CLPI_EP_COARSE;

typedef struct
{
    uint8_t           is_angle_change_point;
    uint8_t           i_end_position_offset;
    int               pts_ep;
    int               spn_ep;
} CLPI_EP_FINE;

typedef struct
{
    uint16_t          pid;
    uint8_t           ep_stream_type;
    int               num_ep_coarse;
    int               num_ep_fine;
    uint32_t          ep_map_stream_start_addr;
    CLPI_EP_COARSE   *coarse;
    CLPI_EP_FINE     *fine;
} CLPI_EP_MAP_ENTRY;

typedef struct
{
    uint8_t           type;
    // ep_map
    uint8_t           num_stream_pid;
    CLPI_EP_MAP_ENTRY *entry;
} CLPI_CPI;

typedef struct
{
    uint32_t          type_indicator;
    uint32_t          type_indicator2;
    uint32_t          sequence_info_start_addr;
    uint32_t          program_info_start_addr;
    uint32_t          cpi_start_addr;
    uint32_t          clip_mark_start_addr;
    uint32_t          ext_data_start_addr;
    CLPI_CLIP_INFO    clip;
    CLPI_SEQ_INFO     sequence;
    CLPI_PROG_INFO    program;
    CLPI_CPI          cpi;
    // skip clip mark & extension data
} CLPI_CL;

uint32_t clpi_lookup_spn(CLPI_CL *cl, uint32_t timestamp, int before, uint8_t stc_id);
uint32_t clpi_access_point(CLPI_CL *cl, uint32_t pkt, int next, int angle_change, uint32_t *time);
CLPI_CL* clpi_parse(char *path, int verbose);
void clpi_free(CLPI_CL *cl);

#endif // _CLPI_PARSE_H_