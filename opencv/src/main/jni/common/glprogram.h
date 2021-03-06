/*
 * By downloading, copying, installing or using the software you agree to this license.
 * If you do not agree to this license, do not download, install,
 * copy or use the software.
 *
 *
 *                           License Agreement
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

#ifndef GLPROGRAM_H_
#define GLPROGRAM_H_

#include "assets.h"

// シェーダプログラムを設定する(createShaderProgramの下請け)
GLuint loadShader(GLenum shaderType, const char* pSource);
// シェーダプログラムをビルド・設定する
GLuint createShaderProgram(
	const char* pVertexSource,
	const char* pFragmentSource,
	GLuint *vertex_shader = NULL, GLuint *fragment_shader = NULL);
// assetsの指定したファイルから頂点シェーダプログラムとフラグメントシェーダプログラムを読み込んでコンパイル・設定する
GLuint createShaderProgram(
	Assets &assets,
	const char *vertexfile,
	const char *fragmentfile,
	GLuint *vertex_shader = NULL, GLuint *fragment_shader = NULL);
// シェーダプログラムを開放する
void disposeProgram(GLuint &shader_program, GLuint &vertex_shader, GLuint &fragment_shader);

#endif /* GLPROGRAM_H_ */
