/*
 * By downloading, copying, installing or using the software you agree to this license.
 * If you do not agree to this license, do not download, install,
 * copy or use the software.
 *
 *
 *                           License Agreement
 *                For Open Source Computer Vision Library
 *                        (3-clause BSD License)
 *
 * Copyright (C) 2014-2017, saki t_saki@serenegiant.com
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *   * Neither the names of the copyright holders nor the names of the contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * This software is provided by the copyright holders and contributors "as is" and
 * any express or implied warranties, including, but not limited to, the implied
 * warranties of merchantability and fitness for a particular purpose are disclaimed.
 * In no event shall copyright holders or contributors be liable for any direct,
 * indirect, incidental, special, exemplary, or consequential damages
 * (including, but not limited to, procurement of substitute goods or services;
 * loss of use, data, or profits; or business interruption) however caused
 * and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of
 * the use of this software, even if advised of the possibility of such damage.
 */

#define LOG_TAG "GLProgram"
#if 1	// デバッグ情報を出さない時は1
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// LOGV/LOGD/MARKを出力しない時
	#endif
	#undef USE_LOGALL			// 指定したLOGxだけを出力
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
	#undef NDEBUG
	#define DEBUG_GL_CHECK			// GL関数のデバッグメッセージを表示する時
#endif

#include <stdio.h>
#include <stdlib.h>

#include "utilbase.h"
#include "glutils.h"
#include "glprogram.h"

// シェーダプログラムをロード・コンパイル
GLuint loadShader(GLenum shaderType, const char *pSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, NULL);
    	GLCHECK("glShaderSource");
        glCompileShader(shader);
    	GLCHECK("glCompileShader");
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    	GLCHECK("glGetShaderiv");
        if (!compiled) {	// コンパイル失敗?
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        	GLCHECK("glGetShaderiv");
            if (infoLen) {
                char *buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, NULL, buf);
                	GLCHECK("glGetShaderInfoLog");
                    LOGE("loadShader:Could not compile shader %d:\n%s\n", shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
            	GLCHECK("glDeleteShader");
                shader = 0;
            }
        }
    }
    return shader;
}

// assetsの指定したファイルから頂点シェーダとフラグメントシェーダを読込み設定する
GLuint createShaderProgram(Assets &assets, const char *vertexfile, const char *fragmentfile, GLuint *vertex_shader, GLuint *fragment_shader) {
	LOGV("createShaderProgramFromAssets:vertex='%s',fragmernt='%s'", vertexfile, fragmentfile);
	// 頂点シェーダプログラムをassetsから読込む
	assets.open(vertexfile);
	int vlen = assets.length();
	char vertexSource[vlen+1];		// 終端マーカ用に1バイト余分に確保する
	assets.read(vertexSource, vlen);
	assets.close();
	vertexSource[vlen] = 0;			// 終端マーカ
	// フラグメントシェーダプログラムをassetsから読込む
	assets.open(fragmentfile);
	int flen = assets.length();
	char fragmentSource[flen+1];	// 終端マーカ用に1バイト余分に確保する
	assets.read(fragmentSource, flen);
	assets.close();
	fragmentSource[flen] = 0;		// 終端マーカ
	// シェーダプログラムをビルド・設定する
	return createShaderProgram(vertexSource, fragmentSource, vertex_shader, fragment_shader);
}

// シェーダプログラムをビルド・設定する
GLuint createShaderProgram(const char *pVertexSource, const char *pFragmentSource, GLuint *vertex_shader, GLuint *fragment_shader) {
	// 頂点シェーダプログラムをロード・コンパイルする
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader) {
    	LOGE("createShaderProgram:fail to compile vertexShader");
        return 0;
    }
    // フラグメントシェーダプログラムをロード・コンパイルする
    GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!fragmentShader) {
    	LOGE("createShaderProgram:fail to compile fragmentShader");
        return 0;
    }

    GLuint program = glCreateProgram();
	GLCHECK("glCreateProgram");
    if (program) {
        glAttachShader(program, vertexShader);
        GLCHECK("glAttachShader");
        glAttachShader(program, fragmentShader);
        GLCHECK("glAttachShader");

        glLinkProgram(program);
    	GLCHECK("glLinkProgram");
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
    	GLCHECK("glGetProgramiv");
        if (linkStatus != GL_TRUE) {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
        	GLCHECK("glGetProgramiv");
            if (bufLength) {
                char* buf = (char*) malloc(bufLength);
                if (buf) {
                    glGetProgramInfoLog(program, bufLength, NULL, buf);
                	GLCHECK("glGetProgramInfoLog");
                    LOGE("createShaderProgram:Could not link program:\n%s\n", buf);
                    free(buf);
                }
            }
            disposeProgram(program, vertexShader, fragmentShader);
        }
    }
    if (LIKELY(program)) {
    	if (vertex_shader)
    		*vertex_shader = vertexShader;
    	if (fragment_shader)
    		*fragment_shader = fragmentShader;
    	LOGV("createShaderProgram:success to create shader program");
    }
    return program;
}

void disposeProgram(GLuint &shader_program, GLuint &vertex_shader, GLuint &fragment_shader) {
	if (LIKELY(shader_program)) {
		if (LIKELY(vertex_shader)) {
			glDetachShader(shader_program, vertex_shader);
        	GLCHECK("glDetachShader");
			glDeleteShader(vertex_shader);
        	GLCHECK("glDeleteShader");
			vertex_shader = 0;
		}
		if (LIKELY(fragment_shader)) {
			glDetachShader(shader_program, fragment_shader);
        	GLCHECK("glDetachShader");
			glDeleteShader(fragment_shader);
        	GLCHECK("glDeleteShader");
			fragment_shader = 0;
		}
		glDeleteProgram(shader_program);
    	GLCHECK("glDeleteProgram");
		shader_program = 0;
	}
}

