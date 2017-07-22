// This file is part of ffind.
//
// Copyright (C) 2017  David Gamba Rios
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.

/*
Package ffind - file listing lib that allows to follow symlinks and skip files or directories based on various criteria.

Library goals:

• Return a file list given a dir.

•  Return the given file given a file.

• Do case insensitive (by default) or sensitive file matching.

• Allow to return files or dirs only.
Maybe build a list of common extensions in the skip code to allow for groups.
For example: '.rb' and '.erb' for ruby files.

• Follow Symlinks.
  • Is there a case where you don't want to? Allow disabling the follow anyway.

• Ignore hidden files (configurable).

  • In windows?

  • In Linux, ignore files starting with .

• Ignore git, svn and mercurial files (configurable).

*/
package ffind

import (
	"log"
	"os"
	"path/filepath"
	"strings"
)

// FileError - Struct containing the File and Error information.
type FileError struct {
	FileInfo os.FileInfo
	Path     string
	Error    error
}

// NewFileError - Given a filepath returns a FileError struct.
func NewFileError(path string) (*FileError, error) {
	log.Printf("NewFileError: %s", path)
	fInfo, err := os.Lstat(path)
	if err != nil {
		log.Printf("NewFileError ERROR: %s", err)
		if os.IsNotExist(err) {
			// Clean up error context
			err = os.ErrNotExist
		}
	}
	return &FileError{fInfo, filepath.Clean(path), err}, err
}

// IsSymlink - Determine if FileError is describing a Symlink.
func (fe *FileError) IsSymlink() bool {
	return fe.FileInfo.Mode()&os.ModeSymlink != 0
}

// ReadDirNoSort - Same as ioutil/ReadDir but doesn't sort results.
// It also cleans up the error from the open call.
//
//   Taken from https://golang.org/src/io/ioutil/ioutil.go
//   Copyright 2009 The Go Authors. All rights reserved.
//   Use of this source code is governed by a BSD-style
//   license that can be found in the LICENSE file.
func ReadDirNoSort(dirname string) ([]os.FileInfo, error) {
	f, err := os.Open(dirname)
	if err != nil {
		log.Printf("ReadDirNoSort ERROR: %s", err)
		if os.IsPermission(err) {
			// Clean up error context to make the output nicer
			err = os.ErrPermission
		}
		return nil, err
	}
	list, err := f.Readdir(-1)
	f.Close()
	if err != nil {
		return nil, err
	}
	return list, nil
}

// ListOneLevel - will return a one level list of FileError results under `path`.
func ListOneLevel(
	path string,
	follow bool,
	sortFn SortFn) <-chan FileError {
	// Error gets passed to fe.Error, OK to ignore.
	fe, _ := NewFileError(path)
	return listOneLevel(fe, follow, sortFn)
}

// listOneLevel - will return a one level list of files under `FileError`.
// If `file` is a regular file, will return a FileError channel with itself.
// If `file` is a symlink and we are not following symlinks, will return a FileError channel with itself.
// If `file` is a symlink and we are following symlinks, will return a FileError channel with the readlink file.
// If `file` is a dir, will return a FileError channel with one level list under the dir.
func listOneLevel(
	fe *FileError,
	follow bool,
	sortFn SortFn) <-chan FileError {
	fInfo := fe.FileInfo
	file := fe.Path
	log.Printf("file: %s\n", file)
	c := make(chan FileError)
	go func() {
		// Check for error
		if fe.Error != nil {
			log.Printf("listOneLevel entry error: %s", fe.Error.Error())
			c <- *fe
			close(c)
			return
		}
		// Check if file is symlink.
		nfe := fe
		if fe.IsSymlink() && follow {
			log.Printf("\tIsSymlink: %s", file)
			eval, err := filepath.EvalSymlinks(fe.Path)
			if err != nil {
				log.Printf("EvalSymlinks error: %s", err)
				// TODO: Clean up error description
				fe.Error = err
				c <- *fe
				close(c)
				return
			}
			nfe, err = NewFileError(eval)
			// TODO: Figure out how to add a test for this!
			if err != nil {
				log.Printf("NewFileError error: %s", err)
				fe.Error = err
				c <- *fe
				close(c)
				return
			}
			log.Printf("\tSymlink: %s", nfe.Path)
		}
		if nfe.FileInfo.IsDir() {
			log.Printf("\tDir: %s\n", fInfo.Name())
			fileMatches, err := ReadDirNoSort(file)
			if err != nil {
				c <- FileError{fInfo, filepath.Join(filepath.Dir(file), fInfo.Name()), err}
				close(c)
				return
			}
			sortFn(fileMatches)
			for _, fm := range fileMatches {
				c <- FileError{fm, filepath.Join(filepath.Clean(file), fm.Name()), err}
				log.Printf("\tFile: %s\n", fm.Name())
			}
			close(c)
			return
		}
		// If file is a regular file return the file and update the path to be the
		// dirname of the file in case of resolved symlinks.
		dirname := filepath.Dir(file)
		c <- FileError{fInfo, filepath.Join(dirname, fInfo.Name()), nil}
		close(c)
		return
	}()
	return c
}

// ListRecursive - will return a recursive list of FileError results under `path`.
func ListRecursive(path string,
	follow bool,
	s FileMatcher, sortFn SortFn) <-chan FileError {
	fe, _ := NewFileError(path)
	return listRecursive(fe, follow, s, sortFn)
}

// listRecursive - will return a recursive list of files under `file`.
// If `file` is a regular file, will return a FileError channel with itself.
// If `file` is a symlink and we are not following symlinks, will return a FileError channel with itself.
// If `file` is a symlink and we are following symlinks, will return a FileError channel with the readlink file.
// If `file` is a dir, will return a FileError channel with one level list under the dir.
func listRecursive(fe *FileError, follow bool, s FileMatcher, sortFn SortFn) <-chan FileError {
	c := make(chan FileError)
	go func() {
		if fe.Error != nil {
			log.Printf("\tError received: %s", fe.Error)
			c <- *fe
			close(c)
			return
		}
		log.Printf("Query: %s", fe.Path)
		ch := listOneLevel(fe, follow, sortFn)
		for e := range ch {
			log.Printf("\tReceived: %s", e.FileInfo.Name())
			if e.Error != nil {
				log.Printf("\tError received: %s", e.Error)
				c <- e
				continue
			}

			// Check if file is symlink.
			ne := &e
			checkSymlink := func() {
				if e.IsSymlink() && follow {
					log.Printf("\tIsSymlink: %s", e.Path)
					eval, err := filepath.EvalSymlinks(e.Path)
					if err != nil {
						log.Printf("\tEvalSymlinks error: %s", err)
						// If the link is broken then just return the original file
						if os.IsNotExist(err) {
							return
						}
						e.Error = err
						return
					}
					ne, err = NewFileError(eval)
					if err != nil {
						log.Printf("\tNew Error received: %s", err)
						e.Error = err
						return
					}
					log.Printf("\tSymlink: %s", ne.Path)
				}
			}
			checkSymlink()

			if ne.FileInfo.IsDir() {
				// TODO: Make sure to test SkipDirName
				if s.SkipDirName(e.FileInfo.Name()) {
					continue
				}
				if !s.SkipDirResults() {
					log.Printf("DIR: %s - %s", e.Path, ne.Path)
					c <- e
				}
				cr := listRecursive(&e, follow, s, sortFn)
				for e := range cr {
					log.Printf("Recurse: %s", e.Path)
					c <- e
				}
			} else {
				// TODO: Make sure to test SkipFileName
				if s.SkipFileResults() || s.SkipFileName(e.FileInfo.Name()) {
					continue
				}
				log.Printf("Else: %s", e.Path)
				if s.MatchFileName(e.FileInfo.Name()) {
					c <- e
				}
			}
		}
		close(c)
		return
	}()
	return c
}

// ListRecursiveWalk - POC of trying to accomplish the same dir walking using filepah.Walk wrappers.
// Works but I don't like it!
// Didn't bother adding a sortFn to it.
// This is how the walk function implementation would be called.
// 	myWalkFn := func(path string, info os.FileInfo, err error) error {
// 		// walkFn is called with the root itself.
// 		// Ignore the root unless it is a file.
// 		log.Printf("result: %s", path)
// 		if err != nil {
// 			fmt.Fprintf(os.Stderr, "ERROR: '%s' %s\n", path, err)
// 			if path == dir && strings.Contains(err.Error(), "no such file or directory") {
// 				return err
// 			}
// 		}
// 		if path == dir {
// 			if info.IsDir() {
// 				return nil
// 			}
// 		}
// 		if r.MatchString(filepath.Base(path)) {
// 			fmt.Println(path)
// 		}
// 		return nil
// 	}
// 	_ = ffind.ListRecursiveWalk(
// 		dir,
// 		follow,
// 		&ffind.BasicIgnore{
// 			IgnoreDirResults:        false,
// 			IgnoreFileResults:       false,
// 			IgnoreVCSDirs:           vcs,
// 			IgnoreHidden:            hidden,
// 			IgnoreFileExtensionList: *ignoreExtensionList,
// 		},
// 		myWalkFn)
func ListRecursiveWalk(root string, follow bool, s FileMatcher, walkFn filepath.WalkFunc) error {
	var listRecursiveWalkFunc filepath.WalkFunc
	listRecursiveWalkFunc = func(path string, info os.FileInfo, pathErr error) error {
		log.Printf("listRecursiveWalkFunc Path: %s", path)
		if pathErr != nil {
			log.Printf("walkFunc error: %s", pathErr)
			if strings.Contains(pathErr.Error(), "no such file or directory") {
				return walkFn(path, info, pathErr)
			}
		}
		if info.IsDir() {
			log.Printf("listRecursiveWalkFunc isDir: %s", info.Name())
			// TODO: Make sure to test SkipDirName
			if s.SkipDirName(info.Name()) {
				return filepath.SkipDir
			}
			if s.SkipDirResults() {
				return pathErr
			}
		} else {
			log.Printf("listRecursiveWalkFunc isFile: %s", info.Name())
			// TODO: Make sure to test SkipFileName
			if s.SkipFileResults() || s.SkipFileName(info.Name()) {
				return pathErr
			}
		}
		return walkFn(path, info, pathErr)
	}
	return SymlinkWalk(root, follow, listRecursiveWalkFunc)
}

// SymlinkWalk - A walkFn that allows to follow symlinks.
func SymlinkWalk(root string, follow bool, walkFn filepath.WalkFunc) error {
	// Declare the WalFunc closure so it can be called recursively
	var symlinkWalkFunc filepath.WalkFunc

	// TODO: This approach might fail if there is an extra level of indirection.
	//       Make sure the closure is done properly.
	// symlinkRoot and symlinkEval are variables used to determine if the current
	// walkFn call was originated from following a symlink.
	// symlinkRoot holds the path of the originating symlink used in the walkFn call.
	var symlinkRoot string
	// symlinkEval holds the evaluated symlink used in the walkFn call.
	var symlinkEval string

	symlinkWalkFunc = func(path string, info os.FileInfo, pathErr error) error {
		log.Printf("Path: %s", path)
		// Log the incomming error. Might want to handle this later on.
		if pathErr != nil {
			log.Printf("walkFunc error: %s", pathErr)
			if strings.Contains(pathErr.Error(), "no such file or directory") {
				return walkFn(path, info, pathErr)
			}
		}
		// If this call is originating from a symlink call then replace the path to
		// have the symlinkRoot as base.
		if symlinkEval != "" {
			path = strings.Replace(path, symlinkEval, symlinkRoot, 1)
			log.Printf("new path: %s", path)
		}
		if follow && info.Mode()&os.ModeSymlink != 0 {
			log.Printf("IsSymlink: %s", path)
			var err error // Make sure we are writing the external symlinkEval
			symlinkEval, err = filepath.EvalSymlinks(path)
			if err != nil {
				log.Printf("EvalSymlinks error: %s", err)
				// If the link is broken then just return the original file
				if strings.Contains(err.Error(), "no such file or directory") {
					return walkFn(path, info, pathErr)
				}
				// TODO: If the error is "EvalSymlinks: too many links" wrapp it to make it nicer.
				return walkFn(path, info, err)
			}
			// Open the evaluated symlink to see if it is a dir.
			symlink, err := os.Open(symlinkEval)
			if err != nil {
				log.Printf("Open error: %s", err)
				return walkFn(path, info, err)
			}
			defer symlink.Close()
			sInfo, err := symlink.Stat()
			if err != nil {
				log.Printf("Stat error: %s", err)
				return walkFn(path, info, err)
			}
			// If the symlink is not a dir, return the path with the original error.
			if !sInfo.IsDir() {
				symlink.Close()
				return walkFn(path, info, pathErr)
			}
			symlink.Close()
			symlinkRoot = path
			log.Printf("symlinkEval: %s, symlinkRoot: %s", symlinkEval, symlinkRoot)
			// Record the current path as the symlinkRoot to make results relative to this path.
			err = filepath.Walk(symlinkEval, symlinkWalkFunc)
			// Clean Up
			symlinkEval = ""
			symlinkRoot = ""
			return err
		}
		return walkFn(path, info, pathErr)
	}
	return filepath.Walk(root, symlinkWalkFunc)
}
